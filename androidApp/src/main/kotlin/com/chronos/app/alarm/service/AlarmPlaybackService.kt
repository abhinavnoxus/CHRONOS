package com.chronos.app.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
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
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.receiver.AlarmDismissReceiver
import com.chronos.app.alarm.receiver.AlarmSnoozeReceiver
import com.chronos.app.alarm.ui.AlarmRingingActivity
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.Alarm
import com.chronos.app.data.model.AlarmHistory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * CHRONOS Alarm Playback Service
 *
 * Foreground service responsible for:
 * - Playing alarm ringtone
 * - Vibrating the device
 * - Showing the alarm notification with Dismiss/Snooze actions
 * - Launching the full-screen alarm activity
 * - Handling dismiss and snooze commands
 * - Cleaning up on stop
 *
 * CRITICAL FIX (V2): This service is the single source of truth for
 * the "alarm is ringing" state. All dismiss/snooze commands go through
 * this service, ensuring audio, vibration, notification, and database
 * state are always synchronized.
 */
@AndroidEntryPoint
class AlarmPlaybackService : Service() {

    @Inject lateinit var database: ChronosDatabase
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    private var currentAlarmId: Long = -1L
    private var isRinging: Boolean = false
    private var volumeRampRunnable: Runnable? = null
    private var currentVolume: Float = 0f

    private val binder = AlarmBinder()

    inner class AlarmBinder : Binder() {
        fun getService(): AlarmPlaybackService = this@AlarmPlaybackService
    }

    companion object {
        private const val TAG = "AlarmPlaybackService"
        const val ACTION_START_ALARM = "com.chronos.action.START_ALARM"
        const val ACTION_DISMISS_ALARM = "com.chronos.action.DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.chronos.action.SNOOZE_ALARM"
        const val ACTION_STOP_SERVICE = "com.chronos.action.STOP_SERVICE"

        const val NOTIFICATION_CHANNEL_ID = "chronos_alarm_channel"
        const val NOTIFICATION_ID = 9001

        const val EXTRA_ALARM_ID = AlarmScheduler.EXTRA_ALARM_ID

        // Singleton reference for UI to query ringing state
        @Volatile
        var instance: AlarmPlaybackService? = null
            private set

        fun isAlarmRinging(): Boolean = instance?.isRinging == true
        fun getCurrentAlarmId(): Long = instance?.currentAlarmId ?: -1L
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                if (alarmId == -1L) {
                    Log.e(TAG, "START_ALARM with invalid ID, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // If already ringing a different alarm, dismiss it first
                if (isRinging && currentAlarmId != alarmId) {
                    Log.w(TAG, "Already ringing alarm $currentAlarmId, dismissing before starting $alarmId")
                    stopAlarmPlayback()
                }

                startAlarm(intent)
            }
            ACTION_DISMISS_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                if (alarmId == currentAlarmId || alarmId == -1L) {
                    dismissAlarm()
                } else {
                    Log.w(TAG, "Dismiss for alarm $alarmId but ringing $currentAlarmId, ignoring")
                }
            }
            ACTION_SNOOZE_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                if (alarmId == currentAlarmId || alarmId == -1L) {
                    snoozeAlarm()
                }
            }
            ACTION_STOP_SERVICE -> {
                stopAlarmPlayback()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmPlayback()
        serviceScope.cancel()
        instance = null
        Log.i(TAG, "Service destroyed")
    }

    // ═══════════════════════════════════════════════════════════
    // ALARM START
    // ═══════════════════════════════════════════════════════════

    private fun startAlarm(intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: ""
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, 0)
        val ringtoneUri = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_RINGTONE)
        val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)
        val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
        val gradualVolume = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_GRADUAL_VOLUME, true)
        val snoozeMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINUTES, 5)

        currentAlarmId = alarmId
        isRinging = true

        // Show foreground notification immediately
        val notification = buildAlarmNotification(alarmId, label, hour, minute, snoozeMinutes)
        startForeground(NOTIFICATION_ID, notification)

        // Mark alarm as ringing in database
        serviceScope.launch {
            database.alarmDao().setAlarmRinging(alarmId, true)
        }

        // Start audio playback
        startAudioPlayback(ringtoneUri, volume, gradualVolume)

        // Start vibration
        if (vibrate) {
            startVibration()
        }

        // Launch the full-screen alarm activity
        launchAlarmActivity(alarmId, label, hour, minute)

        // Reschedule next occurrence (for recurring alarms)
        serviceScope.launch {
            val alarm = database.alarmDao().getAlarmById(alarmId)
            if (alarm != null) {
                alarmScheduler.scheduleAlarm(alarm)
            }
        }

        Log.i(TAG, "Alarm started: id=$alarmId, label=$label")
    }

    // ═══════════════════════════════════════════════════════════
    // AUDIO PLAYBACK
    // ═══════════════════════════════════════════════════════════

    private fun startAudioPlayback(ringtoneUri: String?, targetVolume: Float, gradual: Boolean) {
        try {
            val uri = if (ringtoneUri != null) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmPlaybackService, uri)
                isLooping = true

                if (gradual) {
                    setVolume(0.01f, 0.01f)
                } else {
                    setVolume(targetVolume, targetVolume)
                }

                prepare()
                start()
            }

            if (gradual) {
                startVolumeRamp(targetVolume)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio playback", e)
        }
    }

    private fun startVolumeRamp(targetVolume: Float) {
        currentVolume = 0.01f
        val rampDuration = 30_000L // 30 seconds to full volume
        val steps = 30
        val stepDelay = rampDuration / steps
        val stepIncrement = targetVolume / steps

        volumeRampRunnable = object : Runnable {
            override fun run() {
                if (!isRinging) return

                currentVolume = (currentVolume + stepIncrement).coerceAtMost(targetVolume)
                mediaPlayer?.setVolume(currentVolume, currentVolume)

                if (currentVolume < targetVolume) {
                    handler.postDelayed(this, stepDelay)
                }
            }
        }
        handler.postDelayed(volumeRampRunnable!!, stepDelay)
    }

    private fun stopAudioPlayback() {
        volumeRampRunnable?.let { handler.removeCallbacks(it) }
        volumeRampRunnable = null

        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
        mediaPlayer = null
    }

    // ═══════════════════════════════════════════════════════════
    // VIBRATION
    // ═══════════════════════════════════════════════════════════

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 1000) // Vibration pattern
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // Repeat from index 0
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    // ═══════════════════════════════════════════════════════════
    // NOTIFICATION
    // ═══════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Alarm Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Active alarm notifications with ringtone playback"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null) // Sound is handled by the service
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildAlarmNotification(
        alarmId: Long,
        label: String,
        hour: Int,
        minute: Int,
        snoozeMinutes: Int
    ): Notification {
        val timeText = String.format("%02d:%02d", hour, minute)
        val displayLabel = if (label.isNotEmpty()) label else "Alarm"

        // Full-screen intent (opens the ringing activity)
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_ALARM_HOUR, hour)
            putExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, minute)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            AlarmScheduler.generateRequestCode(alarmId) + 1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(this, AlarmDismissReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            AlarmScheduler.generateRequestCode(alarmId) + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(this, AlarmSnoozeReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            AlarmScheduler.generateRequestCode(alarmId) + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(displayLabel)
            .setContentText(timeText)
            .setSubText("CHRONOS Alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", dismissPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze (${snoozeMinutes}m)", snoozePendingIntent)
            .setDeleteIntent(dismissPendingIntent) // Swipe-away also dismisses
            .build()
    }

    private fun removeNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    // ═══════════════════════════════════════════════════════════
    // DISMISS
    // ═══════════════════════════════════════════════════════════

    fun dismissAlarm() {
        if (!isRinging) {
            Log.w(TAG, "dismissAlarm called but not ringing")
            stopSelf()
            return
        }

        val alarmId = currentAlarmId
        Log.i(TAG, "Dismissing alarm: $alarmId")

        // 1. Stop audio and vibration immediately
        stopAlarmPlayback()

        // 2. Remove notification
        removeNotification()

        // 3. Update database state
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    database.alarmDao().setAlarmRinging(alarmId, false)

                    // Record in history
                    val alarm = database.alarmDao().getAlarmById(alarmId)
                    database.alarmHistoryDao().insertHistory(
                        AlarmHistory(
                            alarmId = alarmId,
                            alarmLabel = alarm?.label ?: "",
                            scheduledTime = System.currentTimeMillis(),
                            actualTime = System.currentTimeMillis(),
                            action = "dismissed"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating database on dismiss", e)
                }
            }

            // 4. Stop the service
            stopSelf()
        }

        isRinging = false
        currentAlarmId = -1L
    }

    // ═══════════════════════════════════════════════════════════
    // SNOOZE
    // ═══════════════════════════════════════════════════════════

    private fun snoozeAlarm() {
        if (!isRinging) return

        val alarmId = currentAlarmId
        Log.i(TAG, "Snoozing alarm: $alarmId")

        // Stop current playback
        stopAlarmPlayback()
        removeNotification()

        // Schedule snooze
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val alarm = database.alarmDao().getAlarmById(alarmId)
                    if (alarm != null) {
                        val snoozeAlarm = alarm.copy(
                            oneTimeDate = System.currentTimeMillis() + (alarm.snoozeMinutes * 60_000L),
                            repeatDays = 0 // One-time snooze
                        )
                        alarmScheduler.scheduleAlarm(snoozeAlarm)

                        database.alarmDao().setAlarmRinging(alarmId, false)

                        database.alarmHistoryDao().insertHistory(
                            AlarmHistory(
                                alarmId = alarmId,
                                alarmLabel = alarm.label,
                                scheduledTime = System.currentTimeMillis(),
                                actualTime = System.currentTimeMillis(),
                                action = "snoozed",
                                snoozeCount = 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error snoozing alarm", e)
                }
            }

            isRinging = false
            currentAlarmId = -1L
            stopSelf()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════

    private fun stopAlarmPlayback() {
        stopAudioPlayback()
        stopVibration()
    }

    // ═══════════════════════════════════════════════════════════
    // LAUNCH ALARM ACTIVITY
    // ═══════════════════════════════════════════════════════════

    private fun launchAlarmActivity(alarmId: Long, label: String, hour: Int, minute: Int) {
        val intent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_ALARM_HOUR, hour)
            putExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, minute)
        }
        startActivity(intent)
    }
}
