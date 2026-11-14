package com.chronos.app.alarm.engine

import com.chronos.app.data.model.Alarm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/** Pure local-date recurrence calculation. java.time resolves DST gaps/overlaps deterministically. */
object AlarmTimeCalculator {
    fun calculateNextTriggerTime(
        alarm: Alarm,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!alarm.isEnabled || alarm.hour !in 0..23 || alarm.minute !in 0..59) return null
        val zone = timeZone.toZoneId()
        val time = LocalTime.of(alarm.hour, alarm.minute)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val exceptions = alarm.exceptionDates.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()

        alarm.oneTimeDate?.let { date ->
            val localDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
            if (localDate in exceptions) return null
            // A nonexistent local time moves forward by the DST gap; an overlap uses the first offset.
            return localDate.atTime(time).atZone(zone).toInstant().toEpochMilli().takeIf { it > now }
        }
        if (!alarm.isRepeating) {
            for (offset in 0L..366L) {
                val date = today.plusDays(offset)
                if (date in exceptions) continue
                val trigger = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (trigger > now) return trigger
            }
            return null
        }
        for (offset in 0L..366L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek.value !in 1..7 || (alarm.repeatDays and (1 shl (date.dayOfWeek.value - 1))) == 0 || date in exceptions) continue
            val trigger = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (trigger > now) return trigger
        }
        return null
    }
}
