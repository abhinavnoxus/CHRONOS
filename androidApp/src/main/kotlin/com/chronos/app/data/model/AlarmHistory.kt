package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Alarm History — tracks dismissed, snoozed, and missed alarms
 */
@Serializable
@Entity(tableName = "alarm_history")
data class AlarmHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alarmId: Long,
    val alarmLabel: String,
    val scheduledTime: Long,
    val actualTime: Long?, // null if missed
    val action: String, // "dismissed", "snoozed", "missed", "auto_dismissed"
    val snoozeCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
