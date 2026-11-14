package com.chronos.shared

import kotlinx.serialization.Serializable

/**
 * Shared models for Kotlin Multiplatform.
 * These are used by both Android and iOS targets.
 */

@Serializable
data class SharedAlarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Int = 0,
    val snoozeMinutes: Int = 5,
    val gradualVolume: Boolean = true
) {
    fun formatTime(is24Hour: Boolean = true): String {
        return if (is24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            String.format("%d:%02d %s", h, minute, amPm)
        }
    }
}

@Serializable
data class SharedReminder(
    val id: Long = 0,
    val title: String,
    val scheduledTime: Long,
    val isCompleted: Boolean = false
)

@Serializable
data class SharedFocusSession(
    val id: Long = 0,
    val label: String = "Focus",
    val focusDuration: Int = 25,
    val breakDuration: Int = 5,
    val completedSessions: Int = 0
)

/**
 * Theme configuration shared across platforms.
 */
@Serializable
data class ThemeConfig(
    val themeMode: String = "dark",
    val glassStyle: String = "luxury",
    val accentColor: String = "gold",
    val animationIntensity: String = "full",
    val is24Hour: Boolean = true,
    val showSeconds: Boolean = true
) {
    companion object {
        val DEFAULT = ThemeConfig()
    }
}
