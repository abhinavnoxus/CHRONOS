package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Snooze commits a separate scheduled instance before stopping current playback. */
@AndroidEntryPoint
class AlarmSnoozeReceiver : BroadcastReceiver() {
    @Inject lateinit var runtimeController: AlarmRuntimeController

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        if (alarmId < 0L || instanceId.isBlank() || !runtimeController.isActive(alarmId, instanceId)) {
            Log.i(TAG, "Ignored duplicate or stale snooze for alarm $alarmId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (runtimeController.snooze(alarmId, instanceId, scheduledAt, isSnooze)) {
                    runtimeController.stopImmediately(alarmId, instanceId)
                } else {
                    Log.e(TAG, "Snooze was not scheduled for alarm $alarmId; playback remains active")
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not snooze alarm $alarmId; playback remains active", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmSnoozeReceiver"
    }
}
