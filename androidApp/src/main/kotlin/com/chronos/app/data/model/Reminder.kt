package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Reminder Model
 */
@Serializable
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val scheduledTime: Long,
    val isCompleted: Boolean = false,
    val isRecurring: Boolean = false,
    val repeatInterval: Long = 0, // milliseconds
    val priority: Int = 0, // 0=normal, 1=high, 2=critical
    val category: String = "general",
    val customSoundUri: String? = null,
    val snoozeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
