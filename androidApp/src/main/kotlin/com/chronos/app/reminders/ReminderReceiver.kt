package com.chronos.app.reminders

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chronos.app.alarm.engine.AlarmTimeCalculator
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.Alarm
import com.chronos.app.di.ApplicationScope
import com.chronos.app.notifications.NotificationHelper
import com.chronos.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var database: ChronosDatabase
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        val at = intent.getLongExtra(ReminderScheduler.EXTRA_AT, 0L)
        val routine = intent.getBooleanExtra(ReminderScheduler.EXTRA_ROUTINE, false)
        if (id < 0L || at <= 0L) return
        val result = goAsync()
        scope.launch {
            try {
                if (intent.action == ACTION_COMPLETE) {
                    if (!routine) {
                        val item = database.reminderDao().getReminderById(id) ?: return@launch
                        // Old notifications must not complete an edited or recurring reminder.
                        if (item.isRecurring || item.isCompleted || item.scheduledTime != at) return@launch
                        database.reminderDao().markCompleted(id)
                        scheduler.cancel(id)
                        context.getSystemService(NotificationManager::class.java).cancel(ReminderScheduler.notificationId(id, false))
                    }
                    return@launch
                }
                if (intent.action != ReminderScheduler.ACTION_REMIND) return@launch
                val title: String
                val message: String
                if (routine) {
                    val item = database.routineDao().getById(id) ?: return@launch
                    if (!item.isEnabled || nextRoutineTime(item, at - 1) != at) return@launch
                    title = item.title; message = item.note.ifBlank { "Your routine is due" }
                    nextRoutineTime(item)?.let { scheduler.scheduleRoutine(item, it) }
                } else {
                    val item = database.reminderDao().getReminderById(id) ?: return@launch
                    if (item.isCompleted) return@launch
                    val expected = ReminderRecurrence.next(item, at - 1)
                    if (expected != at) return@launch // Ignore stale/edit-replaced notification schedules.
                    title = item.title; message = item.description.ifBlank { "CHRONOS reminder" }
                    if (item.isRecurring && item.repeatInterval > 0L) {
                        var next = item.scheduledTime + item.repeatInterval
                        val now = System.currentTimeMillis()
                        if (next <= now) next += ((now - next) / item.repeatInterval + 1) * item.repeatInterval
                        database.reminderDao().updateReminder(item.copy(scheduledTime = next))
                        scheduler.schedule(item.copy(scheduledTime = next))
                    }
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return@launch
                val open = PendingIntent.getActivity(context, ReminderScheduler.request(id, routine),
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW; data = Uri.parse("chronos://app/plan")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDER)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(message)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open)
                if (!routine && database.reminderDao().getReminderById(id)?.isRecurring == false) {
                    val complete = PendingIntent.getBroadcast(context, ReminderScheduler.request(id, false),
                        Intent(context, ReminderReceiver::class.java).apply {
                            action = ACTION_COMPLETE; data = Uri.parse("chronos://complete-reminder/$id")
                            putExtra(ReminderScheduler.EXTRA_ID, id); putExtra(ReminderScheduler.EXTRA_AT, at)
                        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    builder.addAction(android.R.drawable.checkbox_on_background, "Done", complete)
                }
                context.getSystemService(NotificationManager::class.java).notify(ReminderScheduler.notificationId(id, routine), builder.build())
            } catch (exception: Exception) {
                android.util.Log.e("ReminderReceiver", "Could not deliver reminder", exception)
            } finally { result.finish() }
        }
    }
    companion object {
        const val ACTION_COMPLETE = "com.chronos.action.COMPLETE_REMINDER"
        fun nextRoutineTime(routine: com.chronos.app.data.model.Routine, now: Long = System.currentTimeMillis()): Long? =
            AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = routine.hour, minute = routine.minute,
                isEnabled = routine.isEnabled, repeatDays = routine.repeatDays), now)
    }
}
