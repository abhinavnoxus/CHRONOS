package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A recurring local routine. Weekday bits match Alarm: Monday=1 through Sunday=64. */
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 127,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
