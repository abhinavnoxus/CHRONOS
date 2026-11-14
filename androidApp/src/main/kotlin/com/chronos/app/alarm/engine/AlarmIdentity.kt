package com.chronos.app.alarm.engine

/** Pure identity helpers; a definition, occurrence, snooze and notification action are distinct. */
object AlarmIdentity {
    fun instance(alarmId: Long, scheduledAt: Long, isSnooze: Boolean): String =
        "$alarmId:${if (isSnooze) "snooze" else "alarm"}:$scheduledAt"

    fun matches(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean): Boolean =
        alarmId >= 0L && scheduledAt > 0L && instanceId == instance(alarmId, scheduledAt, isSnooze)

    fun notificationId(instanceId: String): Int =
        (instanceId.hashCode() and 0x7fffffff).let { if (it == 0) 1 else it }

    fun requestCode(alarmId: Long, salt: Int): Int =
        ((alarmId xor (alarmId ushr 32)).toInt() xor (salt * 0x45d9f3b)) and 0x7fffffff
}
