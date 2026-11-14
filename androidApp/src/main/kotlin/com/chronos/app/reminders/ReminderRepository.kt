package com.chronos.app.reminders

import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.Reminder
import com.chronos.app.data.model.Routine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Persistence and platform scheduling change together; UI never talks to AlarmManager. */
@Singleton
class ReminderRepository @Inject constructor(
    private val database: ChronosDatabase,
    private val scheduler: ReminderScheduler
) {
    val reminders: Flow<List<Reminder>> = database.reminderDao().getAllReminders()
    val routines: Flow<List<Routine>> = database.routineDao().observeAll()

    suspend fun saveReminder(reminder: Reminder): Long {
        require(reminder.title.isNotBlank()) { "A reminder needs a title" }
        require(!reminder.isRecurring || reminder.repeatInterval >= 60_000L) { "Repeat interval must be at least one minute" }
        val clean = reminder.copy(title = reminder.title.trim().take(80), description = reminder.description.take(300))
        val id = if (clean.id == 0L) database.reminderDao().insertReminder(clean) else {
            database.reminderDao().updateReminder(clean); clean.id
        }
        scheduler.schedule(clean.copy(id = id))
        return id
    }

    suspend fun completeReminder(id: Long) {
        database.reminderDao().markCompleted(id)
        scheduler.cancel(id)
    }

    suspend fun deleteReminder(reminder: Reminder) {
        scheduler.cancel(reminder.id)
        database.reminderDao().deleteReminder(reminder)
    }

    suspend fun saveRoutine(routine: Routine): Long {
        require(routine.title.isNotBlank()) { "A routine needs a title" }
        require(routine.hour in 0..23 && routine.minute in 0..59 && routine.repeatDays in 1..127)
        val clean = routine.copy(title = routine.title.trim().take(80), note = routine.note.take(300))
        val id = if (clean.id == 0L) database.routineDao().insert(clean) else {
            database.routineDao().update(clean); clean.id
        }
        scheduleRoutine(clean.copy(id = id))
        return id
    }

    suspend fun deleteRoutine(id: Long) {
        scheduler.cancelRoutine(id)
        database.routineDao().delete(id)
    }

    suspend fun reconcile() {
        database.reminderDao().getAllRemindersSnapshot().forEach { scheduler.schedule(it) }
        database.routineDao().getAll().forEach(::scheduleRoutine)
    }

    private fun scheduleRoutine(routine: Routine) {
        ReminderReceiver.nextRoutineTime(routine)?.let { scheduler.scheduleRoutine(routine, it) }
    }
}
