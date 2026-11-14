package com.chronos.app.alarm.engine

/** Pure identity helpers; a definition, occurrence, snooze and notification action are distinct. */
object AlarmIdentity {
    fun instance(alarmId: Long, scheduledAt: Long, isSnooze: Boolean): String =
        "$alarmId:${if (isSnooze) "snooze" else "alarm"}:$scheduledAt"

    fun matches(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean): Boolean =
        alarmId >= 0L && scheduledAt > 0L && instanceId == instance(alarmId, scheduledAt, isSnooze)

    fun notificationId(instanceId: String): Int =
        (instanceId.hashCode() and 0x7fffffff).let { if (it == 0) 1 else it }

    /** Resolve the rare 31-bit hash collision without reusing another live alarm's notification. */
    fun availableNotificationId(instanceId: String, occupied: Set<Int>): Int {
        var candidate = notificationId(instanceId)
        while (candidate in occupied) candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        return candidate
    }

    fun requestCode(alarmId: Long, salt: Int): Int =
        ((alarmId xor (alarmId ushr 32)).toInt() xor (salt * 0x45d9f3b)) and 0x7fffffff
}
