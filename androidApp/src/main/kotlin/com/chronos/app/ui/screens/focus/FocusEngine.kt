package com.chronos.app.ui.screens.focus

import com.chronos.app.data.FocusSessionDao
import com.chronos.app.data.model.FocusSession
import com.chronos.app.settings.ChronosSettings
import com.chronos.app.settings.FocusRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Screen-independent focus state machine. All mutations are serialized and persisted. */
@Singleton
class FocusEngine @Inject constructor(
    private val settings: ChronosSettings,
    private val sessionDao: FocusSessionDao,
    private val clock: FocusClock
) {
    private val mutex = Mutex()
    val runtime = settings.focusRuntime

    suspend fun start(label: String, focusMinutes: Int, breakMinutes: Int, longBreakMinutes: Int, rounds: Int) = mutex.withLock {
        val current = settings.focusRuntime.firstValue()
        if (current.isActive) return@withLock
        val now = clock.wallMillis()
        val duration = focusMinutes.coerceIn(5, 180)
        val session = FocusSession(label = label.trim().ifBlank { "Deep work" }, focusDuration = duration,
            breakDuration = breakMinutes.coerceIn(1, 30), longBreakDuration = longBreakMinutes.coerceIn(5, 60),
            sessionsBeforeLongBreak = rounds.coerceIn(2, 8), startedAt = now)
        val id = sessionDao.insertSession(session)
        val millis = duration * 60_000L
        settings.saveFocusRuntime(FocusRuntime(isActive = true, phase = "focus", label = session.label,
            focusMinutes = duration, breakMinutes = session.breakDuration, longBreakMinutes = session.longBreakDuration,
            sessionsBeforeLongBreak = session.sessionsBeforeLongBreak, sessionStartedAt = now,
            deadlineAt = now + millis, deadlineElapsedRealtime = clock.elapsedMillis() + millis,
            bootCount = bootCount(), sessionId = id))
    }

    suspend fun pause() = mutex.withLock {
        val current = settings.focusRuntime.firstValue()
        if (!current.isActive || current.isPaused) return@withLock
        settings.saveFocusRuntime(current.copy(isPaused = true, pausedRemainingMillis = remaining(current), deadlineAt = 0L, deadlineElapsedRealtime = 0L))
    }

    suspend fun resume() = mutex.withLock {
        val current = settings.focusRuntime.firstValue()
        if (!current.isActive || !current.isPaused) return@withLock
        val remaining = current.pausedRemainingMillis.coerceAtLeast(0L)
        settings.saveFocusRuntime(current.copy(isPaused = false, pausedRemainingMillis = 0L,
            deadlineAt = clock.wallMillis() + remaining, deadlineElapsedRealtime = clock.elapsedMillis() + remaining, bootCount = bootCount()))
    }

    suspend fun stop() = mutex.withLock {
        val current = settings.focusRuntime.firstValue()
        if (!current.isActive) return@withLock
        val partial = if (current.phase == "focus") (current.focusMinutes * 60_000L - remaining(current)).coerceAtLeast(0L) else 0L
        finishSession(current, clock.wallMillis(), current.totalFocusMillis + partial)
        settings.clearFocusRuntime()
    }

    /** Catch up every completed phase, not just one, after background/process recreation. */
    suspend fun reconcile(now: Long = clock.wallMillis(), elapsedNow: Long = clock.elapsedMillis(), currentBootCount: Int = clock.bootCount()) = mutex.withLock {
        var current = settings.focusRuntime.firstValue()
        if (!current.isActive || current.isPaused) return@withLock
        var guard = 0
        while (deadline(current, elapsedNow, currentBootCount) <= now && guard++ < 1000) {
            val completedAt = if (current.bootCount == currentBootCount) {
                // Convert the elapsed-realtime deadline back to wall time for this transition.
                now - (elapsedNow - current.deadlineElapsedRealtime).coerceAtLeast(0L)
            } else current.deadlineAt
            if (current.phase == "focus") {
                val completed = current.completedFocusSessions + 1
                val long = completed % current.sessionsBeforeLongBreak.coerceAtLeast(1) == 0
                val minutes = if (long) current.longBreakMinutes else current.breakMinutes
                current = current.copy(phase = if (long) "long_break" else "break", completedFocusSessions = completed,
                    totalFocusMillis = current.totalFocusMillis + current.focusMinutes * 60_000L,
                    deadlineAt = completedAt + minutes * 60_000L,
                    deadlineElapsedRealtime = current.deadlineElapsedRealtime + minutes * 60_000L)
            } else {
                current = current.copy(phase = "focus", deadlineAt = completedAt + current.focusMinutes * 60_000L,
                    deadlineElapsedRealtime = current.deadlineElapsedRealtime + current.focusMinutes * 60_000L)
            }
        }
        settings.saveFocusRuntime(current)
        val session = sessionDao.getActiveSession()
        if (session != null && session.id == current.sessionId) sessionDao.updateSession(session.copy(
            completedSessions = current.completedFocusSessions, totalFocusMinutes = (current.totalFocusMillis / 60_000L).toInt()))
    }

    fun remaining(runtime: FocusRuntime, elapsedNow: Long = clock.elapsedMillis(), currentBootCount: Int = clock.bootCount()): Long {
        if (!runtime.isActive) return 0L
        if (runtime.isPaused) return runtime.pausedRemainingMillis.coerceAtLeast(0L)
        return if (runtime.bootCount == currentBootCount && runtime.deadlineElapsedRealtime > 0L)
            (runtime.deadlineElapsedRealtime - elapsedNow).coerceAtLeast(0L)
        else (runtime.deadlineAt - clock.wallMillis()).coerceAtLeast(0L)
    }

    private fun deadline(runtime: FocusRuntime, elapsedNow: Long, boot: Int): Long =
        if (runtime.bootCount == boot) clock.wallMillis() + (runtime.deadlineElapsedRealtime - elapsedNow) else runtime.deadlineAt

    private suspend fun finishSession(runtime: FocusRuntime, now: Long, totalMillis: Long) {
        val session = sessionDao.getActiveSession()
        if (session != null && session.id == runtime.sessionId) sessionDao.updateSession(session.copy(endedAt = now,
            completedSessions = runtime.completedFocusSessions, totalFocusMinutes = (totalMillis / 60_000L).toInt()))
    }
    private suspend fun kotlinx.coroutines.flow.Flow<FocusRuntime>.firstValue() = kotlinx.coroutines.flow.first(this)
}
