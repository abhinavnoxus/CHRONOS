package com.chronos.app.data

import androidx.room.*
import com.chronos.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour, minute")
    fun getEnabledAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour, minute")
    suspend fun getEnabledAlarmsSync(): List<Alarm>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAllAlarmsSync(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): Alarm?

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun getAlarmByIdSync(id: Long): Alarm?

    @Query("SELECT * FROM alarms WHERE isRinging = 1 LIMIT 1")
    suspend fun getRingingAlarm(): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    @Query("DELETE FROM alarm_instances WHERE alarmId = :alarmId AND state = 'snoozed'")
    suspend fun removePendingSnoozes(alarmId: Long)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)

    @Query("UPDATE alarms SET isEnabled = :enabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE alarms SET isRinging = :ringing WHERE id = :id")
    suspend fun setAlarmRinging(id: Long, ringing: Boolean)

    @Query("SELECT COUNT(*) FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarmCount(): Int

    @Query("UPDATE alarms SET isRinging = 0")
    suspend fun clearRingingFlags()
}

@Dao
interface AlarmInstanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(instance: AlarmInstance): Long

    @Query("SELECT * FROM alarm_instances WHERE instanceId = :instanceId LIMIT 1")
    suspend fun getById(instanceId: String): AlarmInstance?

    @Query("SELECT * FROM alarm_instances WHERE state = 'ringing' ORDER BY triggeredAt DESC LIMIT 1")
    suspend fun getActiveRinging(): AlarmInstance?

    @Query("SELECT * FROM alarm_instances WHERE state = 'ringing' ORDER BY triggeredAt DESC")
    suspend fun getRingingInstances(): List<AlarmInstance>

    @Query("SELECT * FROM alarm_instances WHERE alarmId = :alarmId AND state = 'ringing' LIMIT 1")
    suspend fun getRingingForAlarm(alarmId: Long): AlarmInstance?

    @Query("SELECT * FROM alarm_instances WHERE state = 'snoozed' AND snoozedUntil IS NOT NULL")
    suspend fun getPendingSnoozes(): List<AlarmInstance>

    @Query("UPDATE alarm_instances SET state = 'dismissed', dismissedAt = :dismissedAt WHERE instanceId = :instanceId AND alarmId = :alarmId AND state IN ('ringing', 'snoozed')")
    suspend fun dismissActiveInstance(instanceId: String, alarmId: Long, dismissedAt: Long): Int

    @Query("UPDATE alarm_instances SET state = 'snoozed', snoozedUntil = :snoozedUntil WHERE instanceId = :instanceId AND alarmId = :alarmId AND state = 'ringing'")
    suspend fun snoozeIfRinging(instanceId: String, alarmId: Long, snoozedUntil: Long): Int

    @Query("UPDATE alarm_instances SET state = 'completed' WHERE alarmId = :alarmId AND state = 'snoozed' AND snoozedUntil = :triggeredAt")
    suspend fun completeSnooze(alarmId: Long, triggeredAt: Long): Int

    @Query("UPDATE alarm_instances SET snoozedUntil = :snoozedUntil WHERE instanceId = :instanceId AND state = 'snoozed'")
    suspend fun updateSnoozeDue(instanceId: String, snoozedUntil: Long): Int

    @Query("UPDATE alarm_instances SET state = 'interrupted' WHERE state = 'ringing'")
    suspend fun interruptRingingInstances(): Int

    @Query("UPDATE alarm_instances SET state = 'superseded' WHERE instanceId = :instanceId AND state = 'ringing'")
    suspend fun supersede(instanceId: String): Int
}

@Dao
interface CalendarEventDao {

    @Query("SELECT * FROM calendar_events ORDER BY startTime ASC")
    fun getAllEvents(): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE startTime >= :from AND startTime <= :to ORDER BY startTime ASC")
    fun getEventsBetween(from: Long, to: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE endTime >= :from ORDER BY startTime ASC LIMIT 10")
    suspend fun getUpcomingSnapshot(from: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE startTime >= :from ORDER BY startTime ASC LIMIT 10")
    fun getUpcomingEvents(from: Long): Flow<List<CalendarEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEvent>)

    @Query("DELETE FROM calendar_events WHERE lastSynced < :before")
    suspend fun deleteStale(before: Long)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY scheduledTime ASC")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY scheduledTime ASC")
    fun getActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersSnapshot(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("UPDATE reminders SET isCompleted = 1, completedAt = :timestamp WHERE id = :id")
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface TimerDao {

    @Query("SELECT * FROM timers ORDER BY createdAt DESC")
    fun getAllTimers(): Flow<List<Timer>>

    @Query("SELECT * FROM timers")
    suspend fun getAllTimersSnapshot(): List<Timer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: Timer): Long

    @Update
    suspend fun updateTimer(timer: Timer)

    @Delete
    suspend fun deleteTimer(timer: Timer)
}

@Dao
interface StopwatchLapDao {

    @Query("SELECT * FROM stopwatch_laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    fun getLapsForSession(sessionId: Long): Flow<List<StopwatchLap>>

    @Insert
    suspend fun insertLap(lap: StopwatchLap): Long

    @Query("DELETE FROM stopwatch_laps WHERE sessionId = :sessionId")
    suspend fun deleteLapsForSession(sessionId: Long)
}

@Dao
interface WorldClockDao {

    @Query("SELECT * FROM world_clocks ORDER BY sortOrder ASC")
    fun getAllWorldClocks(): Flow<List<WorldClock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldClock(worldClock: WorldClock): Long

    @Delete
    suspend fun deleteWorldClock(worldClock: WorldClock)
}

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun getSessionsSince(since: Long): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Update
    suspend fun updateSession(session: FocusSession)
}

@Dao
interface AlarmHistoryDao {

    @Query("SELECT * FROM alarm_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<AlarmHistory>>

    @Query("SELECT * FROM alarm_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getHistorySince(since: Long): Flow<List<AlarmHistory>>

    @Query("SELECT * FROM alarm_history WHERE action = 'missed' ORDER BY timestamp DESC")
    fun getMissedAlarms(): Flow<List<AlarmHistory>>

    @Insert
    suspend fun insertHistory(entry: AlarmHistory): Long

    @Query("DELETE FROM alarm_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}


@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY hour, minute") fun observeAll(): Flow<List<Routine>>
    @Query("SELECT * FROM routines") suspend fun getAll(): List<Routine>
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun getById(id: Long): Routine?
    @Insert suspend fun insert(routine: Routine): Long
    @Update suspend fun update(routine: Routine)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun delete(id: Long)
}

