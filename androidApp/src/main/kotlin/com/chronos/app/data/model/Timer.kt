package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Timer Model (multiple timers supported)
 */
@Serializable
@Entity(tableName = "timers")
data class Timer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "",
    val totalDuration: Long, // milliseconds
    val remainingDuration: Long = totalDuration,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val startedAt: Long? = null,
    val pausedAt: Long? = null,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
