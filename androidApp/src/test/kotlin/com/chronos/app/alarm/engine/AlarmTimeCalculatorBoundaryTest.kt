package com.chronos.app.alarm.engine

import com.chronos.app.data.model.Alarm
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

class AlarmTimeCalculatorBoundaryTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0, zoneId: String = "Asia/Kolkata") =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli()

    @Test fun disabledAlarmHasNoTrigger() = assertNull(AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0, isEnabled = false), at(2026, 3, 4), zone))
    @Test fun invalidTimeIsRejected() {
        assertNull(AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 24, minute = 0), at(2026, 3, 4), zone))
        assertNull(AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 60), at(2026, 3, 4), zone))
    }
    @Test fun exactCurrentInstantSchedulesNextOccurrence() {
        val now = at(2026, 3, 4, 7)
        assertEquals(at(2026, 3, 5, 7), AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0), now, zone))
    }
    @Test fun repeatingAlarmCanWaitAWeek() {
        val now = at(2026, 3, 4, 8) // Wednesday
        assertEquals(at(2026, 3, 11, 7), AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0, repeatDays = Alarm.DAY_WEDNESDAY), now, zone))
    }
    @Test fun timezoneChangesLocalWeekday() {
        val now = at(2026, 3, 4, 23, 30)
        val kolkata = AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0, repeatDays = Alarm.DAY_THURSDAY), now, zone)
        val newYork = AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0, repeatDays = Alarm.DAY_THURSDAY), now, TimeZone.getTimeZone("America/New_York"))
        assertEquals(at(2026, 3, 5, 7), kolkata)
        assertEquals(at(2026, 3, 5, 7, zoneId = "America/New_York"), newYork)
    }
    @Test fun springForwardDoesNotScheduleAnInvalidLocalTime() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val now = at(2026, 3, 7, 12, zoneId = "America/New_York")
        val next = AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 2, minute = 30), now, tz)!!
        // java.util.Calendar normalizes the nonexistent 02:30 to 03:30 on this date.
        assertEquals(at(2026, 3, 8, 3, 30, "America/New_York"), next)
        assertTrue(next > now)
    }
    @Test fun fallBackChoosesAUniqueFutureInstant() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val now = at(2026, 10, 31, 12, zoneId = "America/New_York")
        val next = AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 1, minute = 30), now, tz)!!
        assertTrue(next > now)
        assertEquals(at(2026, 11, 1, 1, 30, "America/New_York"), next)
    }
    @Test fun leapDayIsHandled() {
        val now = at(2028, 2, 28, 8)
        assertEquals(at(2028, 2, 29, 7), AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0), now, zone))
    }
    @Test fun yearBoundaryIsHandled() {
        val now = at(2026, 12, 31, 8)
        assertEquals(at(2027, 1, 1, 7), AlarmTimeCalculator.calculateNextTriggerTime(Alarm(hour = 7, minute = 0), now, zone))
    }
}
