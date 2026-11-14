package com.chronos.app.alarm.engine

import com.chronos.app.data.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlarmTimeCalculatorTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    @Test
    fun oneTimeAlarmPastTodayMovesToNextLocalDay() {
        val now = date(2026, Calendar.MARCH, 4, 7, 31)
        val alarm = Alarm(hour = 7, minute = 30)

        val next = AlarmTimeCalculator.calculateNextTriggerTime(alarm, now, zone)

        assertEquals(date(2026, Calendar.MARCH, 5, 7, 30), next)
    }

    @Test
    fun repeatingAlarmUsesSelectedLocalWeekday() {
        // Wednesday, March 4 2026; a Thursday alarm should be tomorrow in local time.
        val now = date(2026, Calendar.MARCH, 4, 22, 0)
        val alarm = Alarm(hour = 6, minute = 15, repeatDays = Alarm.DAY_THURSDAY)

        val next = AlarmTimeCalculator.calculateNextTriggerTime(alarm, now, zone)

        assertEquals(date(2026, Calendar.MARCH, 5, 6, 15), next)
    }

    @Test
    fun repeatingAlarmSkipsAnExceptionDate() {
        val now = date(2026, Calendar.MARCH, 4, 12, 0)
        val excludedThursday = date(2026, Calendar.MARCH, 5, 0, 0)
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            repeatDays = Alarm.DAY_THURSDAY,
            exceptionDates = listOf(excludedThursday)
        )

        val next = AlarmTimeCalculator.calculateNextTriggerTime(alarm, now, zone)

        assertEquals(date(2026, Calendar.MARCH, 12, 8, 0), next)
    }

    @Test
    fun eachScheduledFiringHasAUniqueInstanceIdentity() {
        val regular = AlarmScheduler.instanceId(42L, 1_800_000L, isSnooze = false)
        val nextRegular = AlarmScheduler.instanceId(42L, 2_000_000L, isSnooze = false)
        val snooze = AlarmScheduler.instanceId(42L, 1_800_000L, isSnooze = true)

        org.junit.Assert.assertNotEquals(regular, nextRegular)
        org.junit.Assert.assertNotEquals(regular, snooze)
    }

    @Test
    fun datedOneTimeAlarmIsNotRescheduledAfterItsDate() {
        val scheduledDate = date(2026, Calendar.MARCH, 5, 0, 0)
        val now = date(2026, Calendar.MARCH, 5, 8, 1)
        val alarm = Alarm(hour = 8, minute = 0, oneTimeDate = scheduledDate)

        assertNull(AlarmTimeCalculator.calculateNextTriggerTime(alarm, now, zone))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
