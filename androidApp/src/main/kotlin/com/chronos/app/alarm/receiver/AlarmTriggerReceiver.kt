package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.service.AlarmPlaybackService

/**
 * Receives the alarm trigger from AlarmManager.
 * Starts the foreground AlarmPlaybackService which handles
 * audio, vibration, notification, and the ringing UI.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        Log.i(TAG, "Alarm triggered: id=$alarmId")

        if (alarmId == -1L) {
            Log.e(TAG, "Invalid alarm ID, ignoring")
            return
        }

        // Forward all extras to the service
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_START_ALARM
            putExtras(intent)
        }

        // Use startForegroundService on Android 8+ (we target 31+, always true)
        context.startForegroundService(serviceIntent)
    }
}
