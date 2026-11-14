package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronos.app.alarm.engine.AlarmIdentity
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Explicit Stop action. A notification content tap uses a direct Activity PendingIntent instead. */
@AndroidEntryPoint
class AlarmDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        if (!AlarmIdentity.matches(alarmId, instanceId, scheduledAt, isSnooze)) return
        // Preserve proof of ownership after silencing, so an early tap can write a tombstone.
        val owned = runtimeController.stopImmediately(alarmId, instanceId)
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                // Always consult the authoritative row, even when no service singleton exists.
                runtimeController.dismiss(alarmId, instanceId, scheduledAt, isSnooze, ownershipProven = owned)
            } catch (exception: Exception) {
                Log.e(TAG, "Could not persist dismissal for $instanceId; audio remains stopped", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object { private const val TAG = "AlarmDismissReceiver" }
}
