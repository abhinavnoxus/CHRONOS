package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Stopwatch Lap Model
 */
@Serializable
@Entity(tableName = "stopwatch_laps")
data class StopwatchLap(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val lapNumber: Int,
    val lapTime: Long, // milliseconds
    val totalTime: Long // milliseconds
)
