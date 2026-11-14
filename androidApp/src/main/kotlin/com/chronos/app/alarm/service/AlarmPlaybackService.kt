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
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmRuntimeStore
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.receiver.AlarmDismissReceiver
import com.chronos.app.alarm.receiver.AlarmSnoozeReceiver
import com.chronos.app.alarm.ui.AlarmRingingActivity
import com.chronos.app.data.ChronosDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Active playback only. Persistent alarm-instance state is owned by [AlarmRuntimeController]. */
@AndroidEntryPoint
class AlarmPlaybackService : Service() {
    @Inject lateinit var database: ChronosDatabase
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject lateinit var runtimeStore: AlarmRuntimeStore

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Long = INVALID_ALARM_ID
    private var currentInstanceId: String? = null
    private var isRinging = false
    private var volumeRampRunnable: Runnable? = null
    private var currentVolume = 0f
    private val binder = AlarmBinder()

    inner class AlarmBinder : Binder() {
        fun getService(): AlarmPlaybackService = this@AlarmPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.i(TAG, "Alarm playback service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm(intent)
            ACTION_STOP_SERVICE -> stopPlaybackAndService()
            else -> Log.w(TAG, "Ignoring unsupported service action ${intent?.action}")
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopAlarmPlayback()
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
        Log.i(TAG, "Alarm playback service destroyed")
    }

    private fun startAlarm(intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, INVALID_ALARM_ID)
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        if (alarmId < 0L || instanceId != AlarmScheduler.instanceId(alarmId, scheduledAt, isSnooze)) {
            Log.e(TAG, "Invalid alarm instance; playback was not started")
            stopSelf()
            return
        }

        if (isRinging && currentAlarmId == alarmId && currentInstanceId == instanceId) return
        if (isRinging) stopAlarmPlayback()

        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, 0)
        val ringtoneUri = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_RINGTONE)
        val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f).coerceIn(0f, 1f)
        val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
        val gradualVolume = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_GRADUAL_VOLUME, true)
        val snoozeMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINUTES, 5).coerceAtLeast(1)

        currentAlarmId = alarmId
        currentInstanceId = instanceId
        isRinging = true
        runtimeStore.activate(alarmId, instanceId, scheduledAt, isSnooze)

        // Meet the foreground-service start deadline before doing database or media work.
        startForeground(NOTIFICATION_ID, buildAlarmNotification(
            alarmId = alarmId,
            instanceId = instanceId,
            scheduledAt = scheduledAt,
            isSnooze = isSnooze,
            label = label,
            hour = hour,
            minute = minute,
            snoozeMinutes = snoozeMinutes
        ))

        serviceScope.launch(Dispatchers.IO) {
            try {
                val activated = runtimeController.activate(alarmId, instanceId, scheduledAt, isSnooze)
                withContext(Dispatchers.Main.immediate) {
                    if (!activated) {
                        Log.w(TAG, "Alarm instance $instanceId is stale, disabled, or already dismissed")
                        stopIfCurrent(alarmId, instanceId)
                    } else if (isRinging && currentAlarmId == alarmId && currentInstanceId == instanceId) {
                        startAudioPlayback(ringtoneUri, volume, gradualVolume)
                        if (vibrate) startVibration()
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not persist alarm runtime state", exception)
                withContext(Dispatchers.Main.immediate) { stopIfCurrent(alarmId, instanceId) }
            }
        }
        Log.i(TAG, "Alarm playback started for $instanceId")
    }

    private fun startAudioPlayback(ringtoneUri: String?, targetVolume: Float, gradual: Boolean) {
        stopAudioPlayback()
        val requestedUri = ringtoneUri?.let(Uri::parse)
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val candidate = requestedUri ?: defaultUri
        if (candidate == null) {
            Log.e(TAG, "No alarm or fallback ringtone is available")
            return
        }
        try {
            preparePlayer(candidate, targetVolume, gradual)
        } catch (exception: Exception) {
            Log.e(TAG, "Selected ringtone is unavailable; trying the system alarm sound", exception)
            stopAudioPlayback()
            if (candidate != defaultUri && defaultUri != null) {
                try {
                    preparePlayer(defaultUri, targetVolume, gradual)
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "System alarm sound is unavailable", fallbackException)
                }
            }
        }
    }

    private fun preparePlayer(uri: Uri, targetVolume: Float, gradual: Boolean) {
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        player.setDataSource(this, uri)
        player.isLooping = true
        player.setVolume(if (gradual) 0.01f else targetVolume, if (gradual) 0.01f else targetVolume)
        player.setOnPreparedListener { prepared ->
            if (!isRinging || currentInstanceId == null) {
                prepared.release()
                if (mediaPlayer === prepared) mediaPlayer = null
                return@setOnPreparedListener
            }
            prepared.start()
            if (gradual) startVolumeRamp(targetVolume)
        }
        player.setOnErrorListener { failedPlayer, what, extra ->
            Log.e(TAG, "Ringtone playback failed: what=$what extra=$extra")
            try { failedPlayer.release() } catch (_: IllegalStateException) { }
            if (mediaPlayer === failedPlayer) mediaPlayer = null
            true
        }
        player.prepareAsync()
    }

    private fun startVolumeRamp(targetVolume: Float) {
        currentVolume = 0.01f
        val stepCount = 30
        val stepDelay = 1_000L
        val increment = (targetVolume - currentVolume).coerceAtLeast(0f) / stepCount
        volumeRampRunnable = object : Runnable {
            override fun run() {
                if (!isRinging) return
                currentVolume = (currentVolume + increment).coerceAtMost(targetVolume)
                try { mediaPlayer?.setVolume(currentVolume, currentVolume) } catch (_: IllegalStateException) { }
                if (currentVolume < targetVolume) handler.postDelayed(this, stepDelay)
            }
        }
        handler.postDelayed(volumeRampRunnable!!, stepDelay)
    }

    private fun stopAudioPlayback() {
        volumeRampRunnable?.let(handler::removeCallbacks)
        volumeRampRunnable = null
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: IllegalStateException) {
                // A MediaPlayer that has not completed prepareAsync cannot be stopped yet.
            } finally {
                try { player.release() } catch (_: IllegalStateException) { }
            }
        }
        mediaPlayer = null
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 700, 900), 0))
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Vibration could not be started", exception)
        }
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (exception: RuntimeException) {
            Log.w(TAG, "Vibration could not be stopped cleanly", exception)
        }
        vibrator = null
    }

    private fun stopAlarmPlayback() {
        isRinging = false
        stopAudioPlayback()
        stopVibration()
    }

    private fun stopIfCurrent(alarmId: Long, instanceId: String) {
        if (currentAlarmId == alarmId && currentInstanceId == instanceId) stopPlaybackAndService()
    }

    private fun stopPlaybackAndService() {
        val stoppedAlarmId = currentAlarmId
        val stoppedInstanceId = currentInstanceId
        stopAlarmPlayback()
        if (stoppedAlarmId >= 0L && stoppedInstanceId != null) {
            runtimeStore.clearIfActive(stoppedAlarmId, stoppedInstanceId)
        }
        currentAlarmId = INVALID_ALARM_ID
        currentInstanceId = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Alarm alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active CHRONOS alarms and alarm controls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
        )
    }

    private fun buildAlarmNotification(
        alarmId: Long,
        instanceId: String,
        scheduledAt: Long,
        isSnooze: Boolean,
        label: String,
        hour: Int,
        minute: Int,
        snoozeMinutes: Int
    ): Notification {
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            action = ACTION_SHOW_RINGING
            data = Uri.parse("chronos://ringing/$instanceId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_ALARM_HOUR, hour)
            putExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, minute)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            AlarmScheduler.generateRequestCode(alarmId, 5),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = dismissalPendingIntent(alarmId, instanceId, scheduledAt, isSnooze, openApp = true)
        val dismissIntent = dismissalPendingIntent(alarmId, instanceId, scheduledAt, isSnooze, openApp = false)
        val snoozeIntent = Intent(this, AlarmSnoozeReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            data = Uri.parse("chronos://alarm-action/$instanceId/snooze")
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            AlarmScheduler.generateRequestCode(alarmId, 6),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val timeText = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
        val title = label.ifBlank { if (isSnooze) "Snoozed alarm" else "Alarm" }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("$timeText · CHRONOS alarm is ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            // A content tap dismisses first; the receiver opens the dashboard after stopping playback.
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", dismissIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze ${snoozeMinutes}m", snoozePendingIntent)
            .build()
    }

    private fun dismissalPendingIntent(
        alarmId: Long,
        instanceId: String,
        scheduledAt: Long,
        isSnooze: Boolean,
        openApp: Boolean
    ): PendingIntent {
        val operation = if (openApp) "open" else "stop"
        val dismissIntent = Intent(this, AlarmDismissReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            data = Uri.parse("chronos://alarm-action/$instanceId/$operation")
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(AlarmDismissReceiver.EXTRA_OPEN_APP, openApp)
        }
        return PendingIntent.getBroadcast(
            this,
            AlarmScheduler.generateRequestCode(alarmId, if (openApp) 7 else 8),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // AlarmManager's pending operation and the ongoing service do not depend on an Activity task.
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val TAG = "AlarmPlaybackService"
        private const val INVALID_ALARM_ID = -1L
        const val ACTION_START_ALARM = "com.chronos.action.START_ALARM"
        const val ACTION_DISMISS_ALARM = "com.chronos.action.DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.chronos.action.SNOOZE_ALARM"
        const val ACTION_STOP_SERVICE = "com.chronos.action.STOP_SERVICE"
        const val ACTION_SHOW_RINGING = "com.chronos.action.SHOW_RINGING"
        const val NOTIFICATION_CHANNEL_ID = "chronos_alarm_channel"
        const val NOTIFICATION_ID = 9001

        @Volatile
        var instance: AlarmPlaybackService? = null
            private set

        fun isAlarmRinging(): Boolean = instance?.isRinging == true
        fun getCurrentAlarmId(): Long = instance?.currentAlarmId ?: INVALID_ALARM_ID

        fun stopForInstance(alarmId: Long, instanceId: String): Boolean {
            val service = instance ?: return false
            if (service.currentAlarmId != alarmId || service.currentInstanceId != instanceId) return false
            service.stopPlaybackAndService()
            return true
        }
    }
}
