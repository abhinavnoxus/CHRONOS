package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.chronos.app.data.Converters
import kotlinx.serialization.Serializable

/**
 * CHRONOS Alarm Model
 * Represents a single alarm with all its configuration.
 */
@Serializable
@Entity(tableName = "alarms")
@TypeConverters(Converters::class)
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Hour in 24-hour format (0-23) */
    val hour: Int,

    /** Minute (0-59) */
    val minute: Int,

    /** Whether this alarm is currently enabled */
    val isEnabled: Boolean = true,

    /** User-visible label */
    val label: String = "",

    /** Days of week this alarm repeats (bitmask: Mon=1, Tue=2, ... Sun=64) */
    val repeatDays: Int = 0,

    /** Ringtone URI (null = system default) */
    val ringtoneUri: String? = null,

    /** Volume (0.0 to 1.0) */
    val volume: Float = 0.8f,

    /** Whether to gradually increase volume */
    val gradualVolume: Boolean = true,

    /** Whether to vibrate */
    val vibrate: Boolean = true,

    /** Vibration pattern name */
    val vibrationPattern: String = "default",

    /** Snooze duration in minutes */
    val snoozeMinutes: Int = 5,

    /** Whether this alarm requires a dismissal challenge */
    val requiresChallenge: Boolean = false,

    /** Challenge type: "math", "qr", "shake", "none" */
    val challengeType: String = "none",

    /** Date for one-time alarm (null = recurring only) */
    val oneTimeDate: Long? = null,

    /** Exception dates (timestamps when this alarm should NOT fire) */
    val exceptionDates: List<Long> = emptyList(),

    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),

    /** Last modification timestamp */
    val updatedAt: Long = System.currentTimeMillis(),

    /** Profile tag (e.g., "work", "sleep", "gym") */
    val profile: String = "default",

    /** Whether this alarm is currently ringing */
    val isRinging: Boolean = false,

    /** Notes attached to the alarm */
    val notes: String = "",

    /** Priority: 0=normal, 1=high, 2=critical */
    val priority: Int = 0
) {
    /** Whether this alarm repeats on any day */
    val isRepeating: Boolean get() = repeatDays != 0

    /** Whether this is a one-time alarm (specific date, no repeat) */
    val isOneTime: Boolean get() = !isRepeating && oneTimeDate != null

    fun isDayEnabled(dayOfWeek: Int): Boolean {
        // dayOfWeek: 1=Mon, 2=Tue, ..., 7=Sun
        val bit = 1 shl (dayOfWeek - 1)
        return (repeatDays and bit) != 0
    }

    fun withDayToggled(dayOfWeek: Int): Alarm {
        val bit = 1 shl (dayOfWeek - 1)
        return copy(
            repeatDays = repeatDays xor bit,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Format the time as HH:MM
     */
    fun formatTime(is24Hour: Boolean = true): String {
        return if (is24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            String.format("%d:%02d %s", h, minute, amPm)
        }
    }

    companion object {
        const val DAY_MONDAY = 1
        const val DAY_TUESDAY = 2
        const val DAY_WEDNESDAY = 4
        const val DAY_THURSDAY = 8
        const val DAY_FRIDAY = 16
        const val DAY_SATURDAY = 32
        const val DAY_SUNDAY = 64

        const val WEEKDAYS = DAY_MONDAY or DAY_TUESDAY or DAY_WEDNESDAY or DAY_THURSDAY or DAY_FRIDAY
        const val WEEKENDS = DAY_SATURDAY or DAY_SUNDAY
        const val EVERY_DAY = WEEKDAYS or WEEKENDS
    }
}
