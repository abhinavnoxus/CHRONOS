package com.chronos.app.alarm.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small synchronous process-independent guard used before showing an alarm notification. */
@Singleton
class AlarmRuntimeStore @Inject constructor(
    @ApplicationContext context: Context
) {
    data class ActiveAlarm(val alarmId: Long, val instanceId: String, val scheduledAt: Long, val isSnooze: Boolean)

    private val preferences = context.getSharedPreferences("chronos_alarm_runtime", Context.MODE_PRIVATE)

    @Synchronized
    fun activate(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean) {
        preferences.edit()
            .putLong(KEY_ALARM_ID, alarmId)
            .putString(KEY_INSTANCE_ID, instanceId)
            .putLong(KEY_SCHEDULED_AT, scheduledAt)
            .putBoolean(KEY_IS_SNOOZE, isSnooze)
            .commit()
    }

    @Synchronized
    fun active(): ActiveAlarm? {
        val instanceId = preferences.getString(KEY_INSTANCE_ID, null) ?: return null
        val alarmId = preferences.getLong(KEY_ALARM_ID, INVALID_ALARM_ID)
        if (alarmId == INVALID_ALARM_ID) return null
        return ActiveAlarm(
            alarmId = alarmId,
            instanceId = instanceId,
            scheduledAt = preferences.getLong(KEY_SCHEDULED_AT, 0L),
            isSnooze = preferences.getBoolean(KEY_IS_SNOOZE, false)
        )
    }

    @Synchronized
    fun isActive(alarmId: Long, instanceId: String): Boolean =
        active()?.let { it.alarmId == alarmId && it.instanceId == instanceId } == true

    @Synchronized
    fun clearIfActive(alarmId: Long, instanceId: String): Boolean {
        if (!isActive(alarmId, instanceId)) return false
        return preferences.edit().clear().commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
    }

    companion object {
        private const val KEY_ALARM_ID = "alarm_id"
        private const val KEY_INSTANCE_ID = "instance_id"
        private const val KEY_SCHEDULED_AT = "scheduled_at"
        private const val KEY_IS_SNOOZE = "is_snooze"
        private const val INVALID_ALARM_ID = -1L
    }
}
