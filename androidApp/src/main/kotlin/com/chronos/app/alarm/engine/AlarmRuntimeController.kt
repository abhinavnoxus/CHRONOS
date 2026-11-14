package com.chronos.app.alarm.engine

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.chronos.app.alarm.service.AlarmPlaybackService
import com.chronos.app.alarm.ui.AlarmRingingActivity
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.Alarm
import com.chronos.app.data.model.AlarmHistory
import com.chronos.app.data.model.AlarmInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Central state transitions for one alarm firing. UI and receivers never invent ringing state. */
@Singleton
class AlarmRuntimeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ChronosDatabase,
    private val alarmScheduler: AlarmScheduler,
    private val runtimeStore: AlarmRuntimeStore
) {
    private val activationMutex = Mutex()
    private val snoozeMutex = Mutex()

    fun isActive(alarmId: Long, instanceId: String): Boolean =
        runtimeStore.isActive(alarmId, instanceId)

    fun stopImmediately(alarmId: Long, instanceId: String): Boolean {
        val serviceStopped = AlarmPlaybackService.stopForInstance(alarmId, instanceId)
        val storeCleared = runtimeStore.clearIfActive(alarmId, instanceId)
        if (!serviceStopped && !storeCleared) return false
        AlarmRingingActivity.finishForInstance(instanceId)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(AlarmPlaybackService.NOTIFICATION_ID)
        return true
    }

    suspend fun activate(
        alarmId: Long,
        instanceId: String,
        scheduledAt: Long,
        isSnooze: Boolean
    ): Boolean = activationMutex.withLock {
        if (instanceId != AlarmScheduler.instanceId(alarmId, scheduledAt, isSnooze)) return@withLock false
        if (!runtimeStore.isActive(alarmId, instanceId)) return@withLock false

        val triggeredAt = System.currentTimeMillis()
        val activated = database.withTransaction {
            val alarm = database.alarmDao().getAlarmById(alarmId) ?: return@withTransaction false
            val existing = database.alarmInstanceDao().getById(instanceId)
            if (!isSnooze && !alarm.isEnabled && existing?.state != AlarmInstance.RINGING) {
                return@withTransaction false
            }

            database.alarmInstanceDao().insertIfAbsent(
                AlarmInstance(
                    instanceId = instanceId,
                    alarmId = alarmId,
                    scheduledTimestamp = scheduledAt,
                    state = AlarmInstance.RINGING,
                    triggeredAt = triggeredAt,
                    isSnooze = isSnooze
                )
            )
            val instance = database.alarmInstanceDao().getById(instanceId)
            if (instance?.state != AlarmInstance.RINGING) return@withTransaction false

            val previous = database.alarmInstanceDao().getActiveRinging()
            if (previous != null && previous.instanceId != instanceId) {
                database.alarmInstanceDao().supersede(previous.instanceId)
                database.alarmDao().setAlarmRinging(previous.alarmId, false)
                database.alarmHistoryDao().insertHistory(
                    AlarmHistory(
                        alarmId = previous.alarmId,
                        alarmLabel = database.alarmDao().getAlarmById(previous.alarmId)?.label.orEmpty(),
                        scheduledTime = previous.scheduledTimestamp,
                        actualTime = triggeredAt,
                        action = "superseded"
                    )
                )
            }

            if (isSnooze) {
                database.alarmInstanceDao().completeSnooze(alarmId, scheduledAt)
            } else if (!alarm.isRepeating) {
                // Persist one-shot completion atomically with this firing, including early notification taps.
                database.alarmDao().setAlarmEnabled(alarmId, false)
            }
            database.alarmDao().setAlarmRinging(alarmId, true)
            true
        }
        if (!activated) return@withLock false

        val alarm = database.alarmDao().getAlarmById(alarmId) ?: return@withLock false
        if (!isSnooze) {
            if (alarm.isRepeating) {
                if (!alarmScheduler.scheduleAlarm(alarm)) {
                    Log.e(TAG, "Could not preserve the next occurrence for recurring alarm $alarmId")
                }
            } else {
                // A non-repeating alarm is one firing, not an implicit daily schedule.
                alarmScheduler.cancelRegularAlarm(alarmId)
            }
        }
        true
    }

    suspend fun dismiss(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean): Boolean {
        if (instanceId != AlarmScheduler.instanceId(alarmId, scheduledAt, isSnooze)) return false
        val now = System.currentTimeMillis()
        val changed = database.withTransaction {
            database.alarmInstanceDao().insertIfAbsent(
                AlarmInstance(
                    instanceId = instanceId,
                    alarmId = alarmId,
                    scheduledTimestamp = scheduledAt,
                    state = AlarmInstance.RINGING,
                    triggeredAt = now,
                    isSnooze = isSnooze
                )
            )
            val rows = database.alarmInstanceDao().dismissActiveInstance(instanceId, alarmId, now)
            if (rows == 0) return@withTransaction false

            database.alarmDao().setAlarmRinging(alarmId, false)
            val alarm = database.alarmDao().getAlarmById(alarmId)
            if (!isSnooze && alarm != null && !alarm.isRepeating) {
                database.alarmDao().setAlarmEnabled(alarmId, false)
            }
            database.alarmHistoryDao().insertHistory(
                AlarmHistory(
                    alarmId = alarmId,
                    alarmLabel = alarm?.label.orEmpty(),
                    scheduledTime = scheduledAt,
                    actualTime = now,
                    action = "dismissed"
                )
            )
            true
        }
        if (changed) {
            alarmScheduler.cancelSnooze(alarmId)
            val alarm = database.alarmDao().getAlarmById(alarmId)
            if (alarm?.isEnabled == false) alarmScheduler.cancelRegularAlarm(alarmId)
        }
        return changed
    }

    suspend fun snooze(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean): Boolean =
        snoozeMutex.withLock { snoozeLocked(alarmId, instanceId, scheduledAt, isSnooze) }

    private suspend fun snoozeLocked(
        alarmId: Long,
        instanceId: String,
        scheduledAt: Long,
        isSnooze: Boolean
    ): Boolean {
        if (instanceId != AlarmScheduler.instanceId(alarmId, scheduledAt, isSnooze)) return false
        if (!runtimeStore.isActive(alarmId, instanceId)) return false
        val instance = database.alarmInstanceDao().getById(instanceId) ?: return false
        if (instance.alarmId != alarmId || instance.state != AlarmInstance.RINGING) return false
        val alarm = database.alarmDao().getAlarmById(alarmId) ?: return false
        val now = System.currentTimeMillis()
        val snoozedUntil = now + alarm.snoozeMinutes.coerceAtLeast(1) * 60_000L

        if (!alarmScheduler.scheduleSnooze(alarm, snoozedUntil)) {
            Log.e(TAG, "Could not schedule snooze for alarm $alarmId")
            return false
        }

        val changed = try {
            database.withTransaction {
                database.alarmInstanceDao().insertIfAbsent(
                    AlarmInstance(
                        instanceId = instanceId,
                        alarmId = alarmId,
                        scheduledTimestamp = scheduledAt,
                        state = AlarmInstance.RINGING,
                        triggeredAt = now,
                        isSnooze = isSnooze
                    )
                )
                val rows = database.alarmInstanceDao().snoozeIfRinging(instanceId, alarmId, snoozedUntil)
                if (rows == 0) return@withTransaction false
                database.alarmDao().setAlarmRinging(alarmId, false)
                database.alarmHistoryDao().insertHistory(
                    AlarmHistory(
                        alarmId = alarmId,
                        alarmLabel = alarm.label,
                        scheduledTime = scheduledAt,
                        actualTime = now,
                        action = "snoozed",
                        snoozeCount = 1
                    )
                )
                true
            }
        } catch (exception: Exception) {
            alarmScheduler.cancelSnooze(alarmId)
            throw exception
        }
        if (!changed) alarmScheduler.cancelSnooze(alarmId)
        return changed
    }

    suspend fun recoverAfterBoot() {
        runtimeStore.clear()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(AlarmPlaybackService.NOTIFICATION_ID)
        database.withTransaction {
            database.alarmInstanceDao().interruptRingingInstances()
            database.alarmDao().clearRingingFlags()
        }

        rescheduleSchedules()
    }

    suspend fun rescheduleSchedules() {
        val alarms = database.alarmDao().getAllAlarmsSync()
        alarms.forEach { alarm ->
            if (alarm.isEnabled) alarmScheduler.scheduleAlarm(alarm) else alarmScheduler.cancelAlarm(alarm.id)
        }

        val now = System.currentTimeMillis()
        database.alarmInstanceDao().getPendingSnoozes().forEach { instance ->
            val alarm = database.alarmDao().getAlarmById(instance.alarmId) ?: return@forEach
            val due = maxOf(instance.snoozedUntil ?: now, now + 5_000L)
            if (alarmScheduler.scheduleSnooze(alarm, due)) {
                database.alarmInstanceDao().updateSnoozeDue(instance.instanceId, due)
            } else {
                Log.e(TAG, "Could not restore snooze for alarm ${alarm.id}")
            }
        }
    }

    companion object {
        private const val TAG = "AlarmRuntimeController"
    }
}
