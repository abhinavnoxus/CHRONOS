package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Calendar Event Model
 * Represents a synced calendar event.
 */
@Serializable
@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean = false,
    val color: Int = 0xFFD4A853.toInt(), // Champagne gold default
    val hasAlarm: Boolean = false,
    val hasReminder: Boolean = false,
    val isRecurring: Boolean = false,
    val calendarName: String = "",
    val accountName: String = "",
    val lastSynced: Long = System.currentTimeMillis()
) {
    val durationMinutes: Long get() = (endTime - startTime) / 60_000
}
