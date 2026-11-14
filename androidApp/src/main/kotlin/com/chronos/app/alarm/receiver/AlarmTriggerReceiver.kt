package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.service.AlarmPlaybackService

/** Starts the alarm playback foreground service from the exact system alarm. */
class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID)
        if (alarmId < 0L || instanceId.isNullOrBlank()) {
            Log.e(TAG, "Alarm trigger is missing its alarm or instance identity")
            return
        }

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_START_ALARM
            putExtras(intent)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (exception: Exception) {
            Log.e(TAG, "Could not start alarm playback for $alarmId", exception)
        }
    }

    companion object {
        private const val TAG = "AlarmTriggerReceiver"
    }
}
