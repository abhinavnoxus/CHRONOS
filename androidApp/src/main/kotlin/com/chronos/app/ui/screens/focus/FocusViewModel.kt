package com.chronos.app.ui.screens.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.FocusSessionDao
import com.chronos.app.data.model.FocusSession
import com.chronos.app.settings.ChronosSettings
import com.chronos.app.settings.FocusRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusSessionDao: FocusSessionDao,
    private val settings: ChronosSettings
) : ViewModel() {
    val runtime: StateFlow<FocusRuntime> = settings.focusRuntime
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusRuntime())

    private var transitionInProgress = false

    fun remainingMillis(runtime: FocusRuntime = this.runtime.value): Long {
        if (!runtime.isActive) return 0L
        if (runtime.isPaused) return runtime.pausedRemainingMillis.coerceAtLeast(0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        return if (runtime.bootCount == bootCount && runtime.deadlineElapsedRealtime > 0L) {
            (runtime.deadlineElapsedRealtime - nowElapsed).coerceAtLeast(0L)
        } else {
            (runtime.deadlineAt - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }

    fun start(label: String, focusMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val duration = focusMinutes.coerceIn(5, 180)
            val session = FocusSession(
                label = label.trim().ifBlank { "Deep work" },
                focusDuration = duration,
                breakDuration = 5,
                longBreakDuration = 15,
                sessionsBeforeLongBreak = 4,
                startedAt = now
            )
            val sessionId = focusSessionDao.insertSession(session)
            val durationMillis = duration * 60_000L
            settings.saveFocusRuntime(
                FocusRuntime(
                    isActive = true,
                    phase = PHASE_FOCUS,
                    label = session.label,
                    focusMinutes = duration,
                    breakMinutes = session.breakDuration,
                    longBreakMinutes = session.longBreakDuration,
                    sessionsBeforeLongBreak = session.sessionsBeforeLongBreak,
                    sessionStartedAt = now,
                    deadlineAt = now + durationMillis,
                    deadlineElapsedRealtime = SystemClock.elapsedRealtime() + durationMillis,
                    bootCount = currentBootCount(),
                    sessionId = sessionId
                )
            )
        }
    }

    fun pause() {
        val current = runtime.value
        if (!current.isActive || current.isPaused) return
        viewModelScope.launch {
            val remaining = remainingMillis(current)
            settings.saveFocusRuntime(
                current.copy(
                    isPaused = true,
                    deadlineAt = 0L,
                    deadlineElapsedRealtime = 0L,
                    pausedRemainingMillis = remaining
                )
            )
        }
    }

    fun resume() {
        val current = runtime.value
        if (!current.isActive || !current.isPaused) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            val remaining = current.pausedRemainingMillis.coerceAtLeast(0L)
            settings.saveFocusRuntime(
                current.copy(
                    isPaused = false,
                    deadlineAt = now + remaining,
                    deadlineElapsedRealtime = elapsed + remaining,
                    pausedRemainingMillis = 0L,
                    bootCount = currentBootCount()
                )
            )
        }
    }

    fun stop() {
        val current = runtime.value
        if (!current.isActive) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val partialFocusMillis = if (current.phase == PHASE_FOCUS) {
                (current.focusMinutes * 60_000L - remainingMillis(current)).coerceAtLeast(0L)
            } else 0L
            val actualFocusMinutes = ((current.totalFocusMillis + partialFocusMillis) / 60_000L).toInt()
            val session = focusSessionDao.getActiveSession()
            if (session != null && session.id == current.sessionId) {
                focusSessionDao.updateSession(
                    session.copy(
                        endedAt = now,
                        completedSessions = current.completedFocusSessions,
                        totalFocusMinutes = actualFocusMinutes
                    )
                )
            }
            settings.clearFocusRuntime()
        }
    }

    fun onTimerTick() {
        val current = runtime.value
        if (!current.isActive || current.isPaused || remainingMillis(current) > 0L || transitionInProgress) return
        transitionInProgress = true
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val next = if (current.phase == PHASE_FOCUS) {
                    val completed = current.completedFocusSessions + 1
                    val longBreak = completed % current.sessionsBeforeLongBreak.coerceAtLeast(1) == 0
                    val nextMinutes = if (longBreak) current.longBreakMinutes else current.breakMinutes
                    val millis = nextMinutes * 60_000L
                    current.copy(
                        phase = if (longBreak) PHASE_LONG_BREAK else PHASE_BREAK,
                        completedFocusSessions = completed,
                        totalFocusMillis = current.totalFocusMillis + current.focusMinutes * 60_000L,
                        isPaused = false,
                        deadlineAt = now + millis,
                        deadlineElapsedRealtime = SystemClock.elapsedRealtime() + millis,
                        bootCount = currentBootCount()
                    )
                } else {
                    val millis = current.focusMinutes * 60_000L
                    current.copy(
                        phase = PHASE_FOCUS,
                        isPaused = false,
                        deadlineAt = now + millis,
                        deadlineElapsedRealtime = SystemClock.elapsedRealtime() + millis,
                        pausedRemainingMillis = 0L,
                        bootCount = currentBootCount()
                    )
                }
                settings.saveFocusRuntime(next)
                updateSession(next)
            } finally {
                transitionInProgress = false
            }
        }
    }

    private suspend fun updateSession(runtime: FocusRuntime) {
        val session = focusSessionDao.getActiveSession() ?: return
        if (session.id != runtime.sessionId) return
        focusSessionDao.updateSession(
            session.copy(
                completedSessions = runtime.completedFocusSessions,
                totalFocusMinutes = (runtime.totalFocusMillis / 60_000L).toInt(),
                endedAt = null
            )
        )
    }

    private fun currentBootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    } catch (_: RuntimeException) {
        0
    }

    companion object {
        const val PHASE_FOCUS = "focus"
        const val PHASE_BREAK = "break"
        const val PHASE_LONG_BREAK = "long_break"
    }
}
