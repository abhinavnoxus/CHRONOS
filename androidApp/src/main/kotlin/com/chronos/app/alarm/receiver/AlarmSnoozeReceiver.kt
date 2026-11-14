package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.service.AlarmPlaybackService

/**
 * Handles the SNOOZE action from the alarm notification.
 */
class AlarmSnoozeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmSnoozeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        Log.i(TAG, "Snooze action received for alarm: $alarmId")

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_SNOOZE_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service for snooze", e)
        }
    }
}
