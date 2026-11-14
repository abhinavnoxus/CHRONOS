package com.chronos.app.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Content tap and the visible Stop action share this idempotent dismissal path. */
@AndroidEntryPoint
class AlarmDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var runtimeController: AlarmRuntimeController

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val openApp = intent.getBooleanExtra(EXTRA_OPEN_APP, false)
        val accepted = alarmId >= 0L && instanceId.isNotBlank() &&
            runtimeController.stopImmediately(alarmId, instanceId)

        if (accepted) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    runtimeController.dismiss(alarmId, instanceId, scheduledAt, isSnooze)
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not persist dismissal for alarm $alarmId", exception)
                } finally {
                    if (openApp) withContext(Dispatchers.Main) { launchMain(context) }
                    pendingResult.finish()
                }
            }
        } else {
            Log.i(TAG, "Ignored duplicate or stale dismissal for alarm $alarmId")
            if (openApp) launchMain(context)
        }
    }

    private fun launchMain(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            ContextCompat.startActivity(context, launchIntent, null)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not open CHRONOS after dismissing the notification", exception)
        }
    }

    companion object {
        const val EXTRA_OPEN_APP = "extra_open_app_after_alarm_dismiss"
        private const val TAG = "AlarmDismissReceiver"
    }
}
