package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.service.AlarmPlaybackService

/**
 * Handles the DISMISS action from the alarm notification.
 *
 * This is the critical fix for the V1 bug: tapping the notification
 * or pressing the Stop/Dismiss action MUST reliably stop the alarm.
 *
 * Flow:
 * 1. User taps notification → opens AlarmRingingActivity with DISMISS action
 * 2. User taps "Stop" action button → triggers this receiver directly
 * 3. This receiver sends a command to AlarmPlaybackService to stop
 * 4. Service stops audio, vibration, removes notification, updates DB
 */
class AlarmDismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmDismissReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        Log.i(TAG, "Dismiss action received for alarm: $alarmId")

        // Send dismiss command to the playback service
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_DISMISS_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service for dismiss", e)
        }
    }
}
