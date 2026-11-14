package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * World Clock Entry
 */
@Serializable
@Entity(tableName = "world_clocks")
data class WorldClock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val city: String,
    val country: String,
    val timeZoneId: String,
    val label: String = "",
    val sortOrder: Int = 0
)
