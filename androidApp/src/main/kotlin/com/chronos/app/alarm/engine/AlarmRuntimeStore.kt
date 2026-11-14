package com.chronos.app.alarm.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Durable, exact-instance ownership mirror. Room remains the authoritative lifecycle record. */
@Singleton
class AlarmRuntimeStore @Inject constructor(@ApplicationContext context: Context) {
    data class ActiveAlarm(
        val alarmId: Long,
        val instanceId: String,
        val scheduledAt: Long,
        val isSnooze: Boolean,
        val activatedAt: Long = 0L,
        val notificationId: Int = AlarmIdentity.notificationId(instanceId)
    )

    private val preferences = context.getSharedPreferences("chronos_alarm_runtime", Context.MODE_PRIVATE)

    @Synchronized
    fun activate(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean, notificationId: Int = AlarmIdentity.notificationId(instanceId)) {
        val ids = preferences.getStringSet(KEY_ACTIVE_IDS, emptySet())!!.toMutableSet()
        ids.add(instanceId)
        preferences.edit()
            .putStringSet(KEY_ACTIVE_IDS, ids)
            .putLong(key(instanceId, "alarm"), alarmId)
            .putLong(key(instanceId, "scheduled"), scheduledAt)
            .putBoolean(key(instanceId, "snooze"), isSnooze)
            .putLong(key(instanceId, "activated"), System.currentTimeMillis())
            .putInt(key(instanceId, "notification"), notificationId)
            .commit()
    }

    @Synchronized
    fun active(): ActiveAlarm? = all().maxByOrNull { it.activatedAt }

    @Synchronized
    fun all(): List<ActiveAlarm> {
        val ids = preferences.getStringSet(KEY_ACTIVE_IDS, emptySet()) ?: emptySet()
        return ids.mapNotNull { id ->
            val alarmId = preferences.getLong(key(id, "alarm"), -1L)
            val scheduledAt = preferences.getLong(key(id, "scheduled"), 0L)
            if (alarmId < 0L || !AlarmIdentity.matches(alarmId, id, scheduledAt, preferences.getBoolean(key(id, "snooze"), false))) {
                null
            } else ActiveAlarm(
                alarmId, id, scheduledAt, preferences.getBoolean(key(id, "snooze"), false),
                preferences.getLong(key(id, "activated"), 0L),
                preferences.getInt(key(id, "notification"), AlarmIdentity.notificationId(id))
            )
        }
    }

    @Synchronized
    fun isActive(alarmId: Long, instanceId: String): Boolean =
        preferences.getStringSet(KEY_ACTIVE_IDS, emptySet())!!.contains(instanceId) &&
            preferences.getLong(key(instanceId, "alarm"), -1L) == alarmId

    @Synchronized
    fun clearIfActive(alarmId: Long, instanceId: String): Boolean {
        if (!isActive(alarmId, instanceId)) return false
        val ids = preferences.getStringSet(KEY_ACTIVE_IDS, emptySet())!!.toMutableSet().apply { remove(instanceId) }
        preferences.edit().remove(key(instanceId, "alarm")).remove(key(instanceId, "scheduled"))
            .remove(key(instanceId, "snooze")).remove(key(instanceId, "activated")).remove(key(instanceId, "notification"))
            .putStringSet(KEY_ACTIVE_IDS, ids).commit()
        return true
    }

    @Synchronized
    fun clear() { preferences.edit().clear().commit() }

    private fun key(instanceId: String, field: String) = "instance:$instanceId:$field"

    companion object { private const val KEY_ACTIVE_IDS = "active_instance_ids" }
}
