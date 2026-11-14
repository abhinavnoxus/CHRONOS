package com.chronos.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Persistent identity and lifecycle for one scheduled firing, not the recurring alarm definition. */
@Entity(
    tableName = "alarm_instances",
    indices = [Index(value = ["alarmId", "state"]), Index(value = ["scheduledTimestamp"])]
)
data class AlarmInstance(
    @PrimaryKey val instanceId: String,
    val alarmId: Long,
    val scheduledTimestamp: Long,
    val state: String,
    val triggeredAt: Long? = null,
    val dismissedAt: Long? = null,
    val snoozedUntil: Long? = null,
    val isSnooze: Boolean = false
) {
    companion object {
        const val RINGING = "ringing"
        const val DISMISSED = "dismissed"
        const val SNOOZED = "snoozed"
        const val COMPLETED = "completed"
        const val SUPERSEDED = "superseded"
        const val INTERRUPTED = "interrupted"
    }
}
