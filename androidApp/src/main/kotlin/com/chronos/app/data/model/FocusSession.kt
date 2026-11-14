package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Focus Session Model (Pomodoro)
 */
@Serializable
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "Focus",
    val focusDuration: Int = 25, // minutes
    val breakDuration: Int = 5, // minutes
    val longBreakDuration: Int = 15, // minutes
    val sessionsBeforeLongBreak: Int = 4,
    val completedSessions: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val totalFocusMinutes: Int = 0
)
