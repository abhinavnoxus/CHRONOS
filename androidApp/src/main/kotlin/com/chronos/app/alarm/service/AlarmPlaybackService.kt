package com.chronos.app.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.chronos.app.R
import com.chronos.app.alarm.engine.AlarmIdentity
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmRuntimeStore
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.ui.AlarmRingingActivity
import com.chronos.app.alarm.receiver.AlarmDismissReceiver
import com.chronos.app.alarm.receiver.AlarmSnoozeReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Playback adapter only; instance transitions belong to AlarmRuntimeController. */
@AndroidEntryPoint
class AlarmPlaybackService : Service() {
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject lateinit var runtimeStore: AlarmRuntimeStore

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var mediaThread: HandlerThread
    private lateinit var mediaHandler: Handler
    private val sessions = ConcurrentHashMap<String, PlaybackSession>()
    private var foregroundInstance: String? = null
    private var vibrator: Vibrator? = null

    private class PlaybackSession(
        val alarmId: Long,
        val instanceId: String,
        val notification: Notification,
        val vibrate: Boolean,
        val notificationId: Int
    ) {
        @Volatile var active = true
        @Volatile var player: MediaPlayer? = null
        var ramp: Runnable? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaThread = HandlerThread("CHRONOS alarm media").apply { start() }
        mediaHandler = Handler(mediaThread.looper)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm(intent)
            ACTION_STOP_SERVICE -> stopAll()
            else -> if (sessions.isEmpty()) stopSelf()
        }
        // The exact broadcast is durable in AlarmManager; do not replay a stale start after a kill.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm(intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        if (!AlarmIdentity.matches(alarmId, instanceId, scheduledAt, isSnooze)) {
            stopSelf()
            return
        }
        val existing = sessions[instanceId]
        if (existing != null) {
            if (existing.alarmId == alarmId) return
            stopSession(instanceId)
        }
        val notification = buildAlarmNotification(alarmId, instanceId, scheduledAt, isSnooze,
            intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty(),
            intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_HOUR, 0),
            intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, 0),
            intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINUTES, 5).coerceAtLeast(1))
        val notificationId = AlarmIdentity.availableNotificationId(instanceId, sessions.values.map { it.notificationId }.toSet())
        val session = PlaybackSession(alarmId, instanceId, notification,
            intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true), notificationId)
        sessions[instanceId] = session
        runtimeStore.activate(alarmId, instanceId, scheduledAt, isSnooze, notificationId)
        // Promote before any database/media work to satisfy the FGS startup contract.
        if (foregroundInstance == null) {
            foregroundInstance = instanceId
            ServiceCompat.startForeground(this, notificationId, notification,
                if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } else {
            getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        }
        refreshVibration()

        val uri = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_RINGTONE)
        val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f).coerceIn(0f, 1f)
        val gradual = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_GRADUAL_VOLUME, true)
        scope.launch {
            val activated = try {
                withContext(Dispatchers.IO) { runtimeController.activate(alarmId, instanceId, scheduledAt, isSnooze) }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not activate alarm $instanceId", exception); false
            }
            if (!activated || !session.active || !runtimeStore.isActive(alarmId, instanceId)) {
                stopSession(instanceId)
            } else {
                mediaHandler.post {
                    if (session.active) play(session, uri, volume, gradual)
                }
            }
        }
    }

    /** All MediaPlayer operations, including potentially slow setDataSource, stay off Main. */
    private fun play(session: PlaybackSession, requested: String?, target: Float, gradual: Boolean) {
        val candidates = mutableListOf<Uri>()
        requested?.let { runCatching { candidates.add(Uri.parse(it)) } }
        listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE).forEach {
            runCatching { RingtoneManager.getDefaultUri(it) }.getOrNull()?.let { uri ->
                if (candidates.none { it == uri }) candidates.add(uri)
            }
        }
        prepareCandidate(session, candidates, 0, target, gradual)
    }

    private fun prepareCandidate(session: PlaybackSession, candidates: List<Uri>, index: Int, target: Float, gradual: Boolean) {
        if (!session.active) return
        if (index >= candidates.size) {
            // Always-available local tone when permissions/provider/filesystem media are unavailable.
            preparePlayer(session, Uri.parse("android.resource://$packageName/${R.raw.chronos_fallback}"), target, gradual) {
                Log.e(TAG, "Even the bundled alarm tone could not be played")
            }
            return
        }
        preparePlayer(session, candidates[index], target, gradual) {
            prepareCandidate(session, candidates, index + 1, target, gradual)
        }
    }

    private fun preparePlayer(session: PlaybackSession, uri: Uri, target: Float, gradual: Boolean, fallback: () -> Unit) {
        if (!session.active) return
        var player: MediaPlayer? = null
        var failed = false
        val fail: () -> Unit = {
            if (!failed) {
                failed = true
                player?.let { runCatching { it.release() } }
                if (session.player === player) session.player = null
                if (session.active) fallback()
            }
        }
        try {
            player = MediaPlayer()
            session.player = player
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.setDataSource(this, uri)
            player.isLooping = true
            player.setVolume(if (gradual) 0.01f else target, if (gradual) 0.01f else target)
            player.setOnPreparedListener {
                if (!session.active || failed) { runCatching { it.release() }; return@setOnPreparedListener }
                runCatching { it.start() }.onFailure { fail() }
                if (gradual && session.active) {
                    var current = 0.01f
                    val increment = (target - current).coerceAtLeast(0f) / 30f
                    session.ramp = object : Runnable {
                        override fun run() {
                            if (!session.active) return
                            current = (current + increment).coerceAtMost(target)
                            runCatching { session.player?.setVolume(current, current) }
                            if (current < target) mediaHandler.postDelayed(this, 1_000L)
                        }
                    }
                    mediaHandler.postDelayed(session.ramp!!, 1_000L)
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Alarm media error $what/$extra; trying fallback"); fail(); true
            }
            player.prepareAsync()
        } catch (exception: Exception) {
            Log.w(TAG, "Unavailable alarm media: $uri", exception); fail()
        }
    }

    private fun releaseAudio(session: PlaybackSession) {
        session.ramp?.let(mediaHandler::removeCallbacks)
        session.ramp = null
        mediaHandler.post {
            val player = session.player
            session.player = null
            runCatching { player?.release() }
        }
    }

    private fun refreshVibration() {
        if (sessions.values.none { it.active && it.vibrate }) {
            runCatching { vibrator?.cancel() }; vibrator = null; return
        }
        if (vibrator != null) return
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 700), 0)) }
    }

    private fun stopSession(instanceId: String) {
        mainHandler.post {
            val session = sessions.remove(instanceId) ?: return@post
            session.active = false
            releaseAudio(session)
            runtimeStore.clearIfActive(session.alarmId, instanceId)
            refreshVibration()
            val manager = getSystemService(NotificationManager::class.java)
            if (foregroundInstance == instanceId) {
                val next = sessions.values.firstOrNull { it.active }
                if (next != null) {
                    foregroundInstance = next.instanceId
                    ServiceCompat.startForeground(this, next.notificationId, next.notification,
                        if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
                } else {
                    foregroundInstance = null
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            manager.cancel(session.notificationId)
            sendBroadcast(Intent(AlarmRuntimeController.ACTION_INSTANCE_FINISHED).setPackage(packageName).putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId))
        }
    }

    private fun stopAll() {
        sessions.keys.toList().forEach(::stopSession)
    }

    override fun onDestroy() {
        sessions.values.forEach { it.active = false; releaseAudio(it) }
        sessions.clear()
        runCatching { vibrator?.cancel() }
        scope.cancel()
        mediaThread.quitSafely()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(NotificationChannel(NOTIFICATION_CHANNEL_ID, "Alarm alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Active CHRONOS alarms and alarm controls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        })
    }

    private fun buildAlarmNotification(
        alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean,
        label: String, hour: Int, minute: Int, snoozeMinutes: Int
    ): Notification {
        val identity = Intent().apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
        }
        val fullScreen = Intent(this, AlarmRingingActivity::class.java).apply {
            action = ACTION_SHOW_RINGING
            data = Uri.parse("chronos://ringing/$instanceId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtras(identity)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
        }
        val fullScreenPending = PendingIntent.getActivity(this, AlarmIdentity.requestCode(alarmId, 5), fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Direct Activity PendingIntent: no Android 12+ notification broadcast trampoline.
        val tapIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            action = AlarmRingingActivity.ACTION_DISMISS_FROM_NOTIFICATION
            data = Uri.parse("chronos://dismiss-tap/$instanceId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtras(identity)
        }
        val content = PendingIntent.getActivity(this, AlarmIdentity.requestCode(alarmId, 7), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = actionPending(this, AlarmDismissReceiver::class.java, "stop", alarmId, instanceId, scheduledAt, isSnooze, 8)
        val snooze = actionPending(this, AlarmSnoozeReceiver::class.java, "snooze", alarmId, instanceId, scheduledAt, isSnooze, 6)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label.ifBlank { if (isSnooze) "Snoozed alarm" else "Alarm" })
            .setContentText(String.format(java.util.Locale.getDefault(), "%02d:%02d · Tap to stop", hour, minute))
            .setCategory(NotificationCompat.CATEGORY_ALARM).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true).setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true).setContentIntent(content)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", dismiss)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze ${snoozeMinutes}m", snooze).build()
    }

    companion object {
        private const val TAG = "AlarmPlayback"
        const val ACTION_START_ALARM = "com.chronos.action.START_ALARM"
        const val ACTION_STOP_SERVICE = "com.chronos.action.STOP_SERVICE"
        const val ACTION_SHOW_RINGING = "com.chronos.action.SHOW_RINGING"
        const val NOTIFICATION_CHANNEL_ID = "chronos_alarm_channel"
        const val NOTIFICATION_ID = 9001 // Legacy notification cleanup only; new IDs are per instance.

        @Volatile private var instance: AlarmPlaybackService? = null
        fun isAlarmRinging(): Boolean = instance?.sessions?.values?.any { it.active } == true

        fun stopForInstance(alarmId: Long, instanceId: String): Boolean {
            val service = instance ?: return false
            val session = service.sessions[instanceId] ?: return false
            if (session.alarmId != alarmId) return false
            service.stopSession(instanceId)
            return true
        }

        fun actionPending(
            context: Context, receiver: Class<*>, operation: String, alarmId: Long,
            instanceId: String, scheduledAt: Long, isSnooze: Boolean, salt: Int
        ): PendingIntent {
            val intent = Intent(context, receiver).apply {
                action = "com.chronos.action.$operation"
                data = Uri.parse("chronos://alarm-action/$instanceId/$operation")
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId)
                putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
                putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            }
            return PendingIntent.getBroadcast(context, AlarmIdentity.requestCode(alarmId, salt), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
