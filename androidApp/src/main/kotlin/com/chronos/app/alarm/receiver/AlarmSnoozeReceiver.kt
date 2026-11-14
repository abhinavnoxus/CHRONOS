package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmSnoozeReceiver : BroadcastReceiver() {
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                if (!runtimeController.snooze(alarmId, instanceId, scheduledAt, isSnooze)) {
                    Log.i(TAG, "Ignored stale snooze or scheduling failed for $instanceId")
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not snooze $instanceId; playback remains active", exception)
            } finally { pendingResult.finish() }
        }
    }
    companion object { private const val TAG = "AlarmSnoozeReceiver" }
}
