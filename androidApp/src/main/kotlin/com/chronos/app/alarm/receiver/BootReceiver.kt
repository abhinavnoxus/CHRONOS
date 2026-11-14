package com.chronos.app.alarm.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.reminders.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reconciles AlarmManager with the database after reboot, update, time or timezone changes. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject lateinit var reminderRepository: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isDeviceStart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        val isExactPermissionChange = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        val shouldReconcile = isDeviceStart || isExactPermissionChange ||
            action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED
        if (!shouldReconcile) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (isDeviceStart) runtimeController.recoverAfterBoot()
                else runtimeController.rescheduleSchedules()
                reminderRepository.reconcile()
                Log.i(TAG, "Alarm schedules reconciled after $action")
            } catch (exception: Exception) {
                Log.e(TAG, "Alarm recovery failed after $action", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
