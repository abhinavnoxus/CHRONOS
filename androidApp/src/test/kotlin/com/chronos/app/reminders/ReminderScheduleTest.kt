package com.chronos.app.reminders

import com.chronos.app.data.model.Routine
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class ReminderScheduleTest {
    @Test fun routineUsesWeekdayRecurrence() {
        val now = 1_800_000_000_000L
        val routine = Routine(id = 1, title = "Review", hour = 9, minute = 0, repeatDays = 31)
        val next = ReminderReceiver.nextRoutineTime(routine, now)!!
        assertTrue(next > now)
        val local = java.util.Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = next }
        assertTrue(local.get(java.util.Calendar.DAY_OF_WEEK) in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY)
        assertEquals(9, local.get(java.util.Calendar.HOUR_OF_DAY))
    }
    @Test fun disabledRoutineHasNoOccurrence() {
        assertNull(ReminderReceiver.nextRoutineTime(Routine(title = "Paused", hour = 9, minute = 0, isEnabled = false)))
    }
    @Test fun reminderAndRoutinePendingIntentNamespacesDiffer() {
        assertNotEquals(ReminderScheduler.request(1, false), ReminderScheduler.request(1, true))
        assertNotEquals(ReminderScheduler.notificationId(1, false), ReminderScheduler.notificationId(1, true))
    }
    @Test fun overdueRecurringReminderSkipsToFutureWithoutOverflow() {
        val item = com.chronos.app.data.model.Reminder(title = "Hydrate", scheduledTime = 1000L, isRecurring = true, repeatInterval = 60_000L)
        assertEquals(1_801_000L, ReminderRecurrence.next(item, 1_800_000L))
        assertNull(ReminderRecurrence.next(item.copy(scheduledTime = Long.MAX_VALUE - 10, repeatInterval = Long.MAX_VALUE / 2), Long.MAX_VALUE - 1))
    }
}
