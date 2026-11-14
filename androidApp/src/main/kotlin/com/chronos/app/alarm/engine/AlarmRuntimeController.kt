package com.chronos.app.alarm.engine

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.chronos.app.alarm.service.AlarmPlaybackService
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.AlarmHistory
import com.chronos.app.data.model.AlarmInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single authority for the lifecycle of one alarm firing.
 *
 * The Room `alarm_instances` row is the source of truth. The SharedPreferences slot and the
 * playback-service singleton are only fast, best-effort mirrors used to stop audio without
 * waiting for a database round trip. A dismissal is therefore honoured even when the process
 * was recreated, the service singleton is gone, or the tap arrives before activation committed.
 */
@Singleton
class AlarmRuntimeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ChronosDatabase,
    private val alarmScheduler: AlarmScheduler,
    private val runtimeStore: AlarmRuntimeStore
) {
    // All authoritative lifecycle transitions are serialized. Fast audio stopping is not delayed.
    private val transitionMutex = Mutex()
    private val activationMutex = transitionMutex
    private val snoozeMutex = transitionMutex
    private val dismissalMutex = transitionMutex

    fun isActive(alarmId: Long, instanceId: String): Boolean =
        runtimeStore.isActive(alarmId, instanceId)

    /** True when the fast mirror or the authoritative instance row still says this firing is live. */
    suspend fun isInstanceRinging(alarmId: Long, instanceId: String): Boolean {
        if (runtimeStore.isActive(alarmId, instanceId)) return true
        val instance = database.alarmInstanceDao().getById(instanceId) ?: return false
        return instance.alarmId == alarmId && instance.state == AlarmInstance.RINGING
    }

    /**
     * Immediately silences audio/vibration/notification when this exact instance owns them.
     * Returns true when a fast mirror was cleared. Never touches another alarm's playback.
     */
    fun stopImmediately(alarmId: Long, instanceId: String): Boolean {
        val storeCleared = runtimeStore.clearIfActive(alarmId, instanceId)
        val serviceStopped = AlarmPlaybackService.stopForInstance(alarmId, instanceId)
        if (serviceStopped || storeCleared) {
            notificationManager().cancel(AlarmIdentity.notificationId(instanceId))
            finishRingingUi(instanceId)
        }
        return serviceStopped || storeCleared
    }

    suspend fun activate(
        alarmId: Long,
        instanceId: String,
        scheduledAt: Long,
        isSnooze: Boolean
    ): Boolean = activationMutex.withLock {
        if (!isValidInstance(alarmId, instanceId, scheduledAt, isSnooze)) return@withLock false
        if (!runtimeStore.isActive(alarmId, instanceId)) return@withLock false

        val triggeredAt = System.currentTimeMillis()
        val activated = database.withTransaction {
            val alarm = database.alarmDao().getAlarmById(alarmId) ?: return@withTransaction false
            val existing = database.alarmInstanceDao().getById(instanceId)
            // A dismissal tombstone (early tap) or any terminal state blocks activation.
            if (existing != null && existing.state != AlarmInstance.RINGING) return@withTransaction false
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

            // Simultaneous alarms keep independent rows, playback sessions and notifications.
            // Never dismiss or supersede another alarm to make room for this one.

            if (isSnooze) {
                database.alarmInstanceDao().completeSnooze(alarmId, scheduledAt)
            } else if (!alarm.isRepeating) {
                database.alarmDao().setAlarmEnabled(alarmId, false)
            }
            database.alarmDao().setAlarmRinging(alarmId, true)
            true
        }
        if (!activated || !runtimeStore.isActive(alarmId, instanceId)) return@withLock false

        val alarm = database.alarmDao().getAlarmById(alarmId) ?: return@withLock false
        if (!isSnooze) {
            if (alarm.isRepeating) {
                if (!alarmScheduler.scheduleAlarm(alarm)) {
                    Log.e(TAG, "Could not preserve the next occurrence for recurring alarm $alarmId")
                }
            } else {
                alarmScheduler.cancelRegularAlarm(alarmId)
            }
        }
        true
    }

    /**
     * Idempotent, database-authoritative dismissal.
     *
     * - Stops playback synchronously before any suspension so a tap never waits on disk.
     * - If the instance row is still ringing/snoozed, transitions it to dismissed.
     * - If the row does not exist yet (tap arrived before activation committed), writes a
     *   DISMISSED tombstone so the pending activation refuses to start audio.
     * - Repeated or stale taps change nothing and create no duplicate history.
     */
    suspend fun dismiss(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean): Boolean {
        if (!isValidInstance(alarmId, instanceId, scheduledAt, isSnooze)) return false
        val hadRuntimeOwnership = isActive(alarmId, instanceId)
        stopImmediately(alarmId, instanceId)
        return dismissalMutex.withLock { dismissLocked(alarmId, instanceId, scheduledAt, isSnooze, hadRuntimeOwnership) }
    }

    private suspend fun dismissLocked(
        alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean, hadRuntimeOwnership: Boolean
    ): Boolean {
        val now = System.currentTimeMillis()
        val changed = database.withTransaction {
            val existing = database.alarmInstanceDao().getById(instanceId)
            val tombstoneInserted = if (existing == null) {
                // Only the notification/service mirror can prove an uncommitted firing is real.
                // Arbitrary expired broadcasts must not manufacture alarm history.
                if (!hadRuntimeOwnership) return@withTransaction false
                database.alarmInstanceDao().insertIfAbsent(
                    AlarmInstance(
                        instanceId = instanceId,
                        alarmId = alarmId,
                        scheduledTimestamp = scheduledAt,
                        state = AlarmInstance.DISMISSED,
                        dismissedAt = now,
                        triggeredAt = now,
                        isSnooze = isSnooze
                    )
                ) != -1L
            } else false
            if (existing != null && (existing.alarmId != alarmId || existing.state !in setOf(AlarmInstance.RINGING, AlarmInstance.SNOOZED))) {
                return@withTransaction false
            }
            val rows = database.alarmInstanceDao().dismissActiveInstance(instanceId, alarmId, now)
            if (rows == 0 && !tombstoneInserted) return@withTransaction false

            database.alarmDao().setAlarmRinging(alarmId, database.alarmInstanceDao().getRingingForAlarm(alarmId) != null)
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
            if (isSnooze) alarmScheduler.cancelSnooze(alarmId)
            val alarm = database.alarmDao().getAlarmById(alarmId)
            if (alarm?.isEnabled == false) alarmScheduler.cancelRegularAlarm(alarmId)
            notificationManager().cancel(AlarmIdentity.notificationId(instanceId))
            finishRingingUi(instanceId)
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
        if (!isValidInstance(alarmId, instanceId, scheduledAt, isSnooze)) return false
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
                val rows = database.alarmInstanceDao().snoozeIfRinging(instanceId, alarmId, snoozedUntil)
                if (rows == 0) return@withTransaction false
                database.alarmDao().setAlarmRinging(alarmId, database.alarmInstanceDao().getRingingForAlarm(alarmId) != null)
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
        if (!changed) {
            alarmScheduler.cancelSnooze(alarmId)
        } else {
            // Playback stops only after the snooze is durably scheduled and recorded.
            stopImmediately(alarmId, instanceId)
        }
        return changed
    }

    suspend fun recoverAfterBoot() {
        val ringing = database.alarmInstanceDao().getRingingInstances()
        runtimeStore.clear()
        ringing.forEach { notificationManager().cancel(AlarmIdentity.notificationId(it.instanceId)) }
        notificationManager().cancel(AlarmPlaybackService.NOTIFICATION_ID)
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.alarmInstanceDao().interruptRingingInstances()
            database.alarmDao().clearRingingFlags()
            ringing.forEach { instance ->
                database.alarmHistoryDao().insertHistory(AlarmHistory(
                    alarmId = instance.alarmId,
                    alarmLabel = database.alarmDao().getAlarmById(instance.alarmId)?.label.orEmpty(),
                    scheduledTime = instance.scheduledTimestamp, actualTime = null,
                    action = "missed", timestamp = now
                ))
            }
        }
        // rescheduleSchedules restores pending snoozes exactly once.
        rescheduleSchedules()
    }

    /** Re-register definitions after an OEM/process kill without destroying a tappable alarm instance. */
    suspend fun reconcileOnAppStart() {
        // A missing service singleton is not proof of dismissal: the process may have been recreated
        // by this very tap. Keep instance identity until dismiss/boot recovery resolves it.
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

    private fun isValidInstance(alarmId: Long, instanceId: String, scheduledAt: Long, isSnooze: Boolean): Boolean =
        AlarmIdentity.matches(alarmId, instanceId, scheduledAt, isSnooze)

    private fun finishRingingUi(instanceId: String) {
        context.sendBroadcast(android.content.Intent(ACTION_INSTANCE_FINISHED).setPackage(context.packageName)
            .putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, instanceId))
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "AlarmRuntimeController"
        const val ACTION_INSTANCE_FINISHED = "com.chronos.action.INSTANCE_FINISHED"
    }
}
