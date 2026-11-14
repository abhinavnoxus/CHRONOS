package com.chronos.app.reminders

import com.chronos.app.data.model.Reminder

object ReminderRecurrence {
    fun next(reminder: Reminder, now: Long): Long? {
        if (reminder.isCompleted) return null
        if (!reminder.isRecurring) return reminder.scheduledTime.takeIf { it > now }
        val interval = reminder.repeatInterval
        if (interval < 60_000L) return null
        val start = reminder.scheduledTime
        if (start > now) return start
        val steps = (now - start) / interval + 1
        return if (steps > (Long.MAX_VALUE - start) / interval) null else start + steps * interval
    }
}
