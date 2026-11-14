package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.data.ChronosDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles device boot, app update, timezone change, and manual clock change.
 * Reschedules all enabled alarms to ensure they survive restarts.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var database: ChronosDatabase

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                rescheduleAllAlarms()
            }
        }
    }

    private fun rescheduleAllAlarms() {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val alarms = database.alarmDao().getEnabledAlarmsSync()
                alarmScheduler.rescheduleAllAlarms(alarms)
                Log.i(TAG, "Rescheduled ${alarms.size} alarms after boot/timezone change")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
