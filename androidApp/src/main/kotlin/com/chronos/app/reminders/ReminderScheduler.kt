package com.chronos.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.chronos.app.data.model.Reminder
import com.chronos.app.data.model.Routine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Exact reminders use notification broadcasts, not the alarm audio foreground service. */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder): Boolean {
        cancel(reminder.id)
        if (reminder.isCompleted || reminder.scheduledTime <= System.currentTimeMillis()) return false
        val next = if (reminder.isRecurring && reminder.repeatInterval >= 60_000L) ReminderRecurrence.next(reminder, System.currentTimeMillis()) ?: return false else reminder.scheduledTime
        return scheduleAt(next, pendingIntent(reminder.id, reminder.title, next, false))
    }

    fun scheduleRoutine(routine: Routine, triggerAt: Long): Boolean {
        cancelRoutine(routine.id)
        if (!routine.isEnabled || triggerAt <= System.currentTimeMillis()) return false
        return scheduleAt(triggerAt, pendingIntent(routine.id, routine.title, triggerAt, true))
    }

    private fun scheduleAt(triggerAt: Long, operation: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        true
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Reminder schedule rejected", exception)
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation) }.isSuccess
    }

    fun cancel(id: Long) = cancelIntent(id, false)
    fun cancelRoutine(id: Long) = cancelIntent(id, true)

    private fun cancelIntent(id: Long, routine: Boolean) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("chronos://${if (routine) "routine" else "reminder"}/$id")
        }
        PendingIntent.getBroadcast(context, request(id, routine), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            manager.cancel(it); it.cancel()
        }
    }

    private fun pendingIntent(id: Long, title: String, at: Long, routine: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("chronos://${if (routine) "routine" else "reminder"}/$id")
            putExtra(EXTRA_ID, id); putExtra(EXTRA_TITLE, title); putExtra(EXTRA_AT, at); putExtra(EXTRA_ROUTINE, routine)
        }
        return PendingIntent.getBroadcast(context, request(id, routine), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_REMIND = "com.chronos.action.REMIND"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AT = "at"
        const val EXTRA_ROUTINE = "routine"
        private const val TAG = "ReminderScheduler"
        fun request(id: Long, routine: Boolean): Int = ((id xor (id ushr 32)).toInt() xor if (routine) 0x52000000 else 0x42000000) and 0x7fffffff
        fun notificationId(id: Long, routine: Boolean): Int = (if (routine) 24000000 else 20000000) + (id % 1000000).toInt()
    }
}
