package com.chronos.app.alarm.engine

import com.chronos.app.data.model.Alarm
import java.util.Calendar
import java.util.TimeZone

/** Pure time calculation shared by scheduling code and regression tests. */
object AlarmTimeCalculator {
    fun calculateNextTriggerTime(
        alarm: Alarm,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!alarm.isEnabled || alarm.hour !in 0..23 || alarm.minute !in 0..59) return null

        val todayAtAlarmTime = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        alarm.oneTimeDate?.let { date ->
            val oneTime = Calendar.getInstance(timeZone).apply {
                timeInMillis = date
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return oneTime.timeInMillis.takeIf { it > now && !isExceptionDate(alarm, oneTime, timeZone) }
        }

        if (!alarm.isRepeating) {
            if (todayAtAlarmTime.timeInMillis <= now) todayAtAlarmTime.add(Calendar.DAY_OF_YEAR, 1)
            for (offset in 0..365) {
                val candidate = (todayAtAlarmTime.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                if (!isExceptionDate(alarm, candidate, timeZone)) return candidate.timeInMillis
            }
            return null
        }

        for (offset in 0..365) {
            val candidate = (todayAtAlarmTime.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val day = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> 7
                else -> candidate.get(Calendar.DAY_OF_WEEK) - 1
            }
            if (alarm.isDayEnabled(day) && candidate.timeInMillis > now && !isExceptionDate(alarm, candidate, timeZone)) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    private fun isExceptionDate(alarm: Alarm, candidate: Calendar, timeZone: TimeZone): Boolean {
        if (alarm.exceptionDates.isEmpty()) return false
        return alarm.exceptionDates.any { exception ->
            val exceptionDate = Calendar.getInstance(timeZone).apply { timeInMillis = exception }
            candidate.get(Calendar.YEAR) == exceptionDate.get(Calendar.YEAR) &&
                candidate.get(Calendar.DAY_OF_YEAR) == exceptionDate.get(Calendar.DAY_OF_YEAR)
        }
    }
}
