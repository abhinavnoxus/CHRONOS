package com.chronos.app.ui.screens.clock

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.StopwatchLapDao
import com.chronos.app.data.WorldClockDao
import com.chronos.app.data.model.StopwatchLap
import com.chronos.app.data.model.Timer
import com.chronos.app.data.model.WorldClock
import com.chronos.app.clock.TimerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopwatchState(
    val running: Boolean = false,
    val baseElapsed: Long = 0L,
    val accumulated: Long = 0L,
    val sessionId: Long = 0L,
    val laps: List<StopwatchLap> = emptyList()
) {
    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long = accumulated + if (running) (now - baseElapsed).coerceAtLeast(0L) else 0L
}

@HiltViewModel
class TimeToolsViewModel @Inject constructor(
    private val database: ChronosDatabase,
    private val worldClockDao: WorldClockDao,
    private val lapDao: StopwatchLapDao,
    private val timerScheduler: TimerScheduler,
    private val stopwatchStore: StopwatchStore
) : ViewModel() {
    val timers = database.timerDao().getAllTimers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val worldClocks = worldClockDao.getAllWorldClocks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _stopwatch = MutableStateFlow(stopwatchStore.load())
    val stopwatch = _stopwatch.asStateFlow()
    init {
        viewModelScope.launch {
            val state = _stopwatch.value
            if (state.sessionId != 0L) _stopwatch.value = state.copy(laps = lapDao.getLapsSnapshot(state.sessionId))
        }
    }
    private fun updateStopwatch(state: StopwatchState) { _stopwatch.value = state; stopwatchStore.save(state) }
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    fun startTimer(label: String, minutes: Int) = viewModelScope.launch {
        runCatching {
            val duration = minutes.coerceIn(1, 1440) * 60_000L
            val now = System.currentTimeMillis()
            val timer = Timer(label = label.trim().take(60), totalDuration = duration, isRunning = true, startedAt = now)
            val id = database.timerDao().insertTimer(timer)
            timerScheduler.schedule(timer.copy(id = id))
        }.onFailure { _error.value = "Could not start timer" }
    }
    fun pauseTimer(timer: Timer) = viewModelScope.launch {
        val remaining = remaining(timer)
        timerScheduler.cancel(timer.id)
        database.timerDao().updateTimer(timer.copy(isRunning = false, isPaused = true, remainingDuration = remaining, startedAt = null))
    }
    fun resumeTimer(timer: Timer) = viewModelScope.launch {
        val updated = timer.copy(isRunning = true, isPaused = false, startedAt = System.currentTimeMillis())
        database.timerDao().updateTimer(updated); timerScheduler.schedule(updated)
    }
    fun resetTimer(timer: Timer) = viewModelScope.launch {
        timerScheduler.cancel(timer.id)
        database.timerDao().updateTimer(timer.copy(isRunning = false, isPaused = false, remainingDuration = timer.totalDuration, startedAt = null))
    }
    fun deleteTimer(timer: Timer) = viewModelScope.launch { timerScheduler.cancel(timer.id); database.timerDao().deleteTimer(timer) }
    fun remaining(timer: Timer, now: Long = System.currentTimeMillis()): Long =
        if (timer.isRunning && timer.startedAt != null) (timer.remainingDuration - (now - timer.startedAt)).coerceAtLeast(0L) else timer.remainingDuration

    fun toggleStopwatch() {
        val current = _stopwatch.value
        updateStopwatch(if (current.running) current.copy(running = false, accumulated = current.elapsed())
        else current.copy(running = true, baseElapsed = SystemClock.elapsedRealtime(), sessionId = if (current.sessionId == 0L) System.currentTimeMillis() else current.sessionId))
    }
    fun lap() {
        val current = _stopwatch.value
        if (!current.running) return
        val total = current.elapsed()
        val lap = StopwatchLap(sessionId = current.sessionId, lapNumber = current.laps.size + 1,
            lapTime = total - (current.laps.lastOrNull()?.totalTime ?: 0L), totalTime = total)
        updateStopwatch(current.copy(laps = current.laps + lap))
        viewModelScope.launch { runCatching { lapDao.insertLap(lap) }.onFailure { _error.value = "Lap shown but could not be saved" } }
    }
    fun resetStopwatch() {
        val old = _stopwatch.value
        updateStopwatch(StopwatchState())
        viewModelScope.launch { lapDao.deleteLapsForSession(old.sessionId) }
    }
    fun addWorldClock(city: String, zone: String) = viewModelScope.launch {
        runCatching { worldClockDao.insertWorldClock(WorldClock(city = city, country = zone.substringAfterLast('/').replace('_', ' '), timeZoneId = zone, sortOrder = worldClocks.value.size)) }
            .onFailure { _error.value = "Could not add world clock" }
    }
    fun removeWorldClock(clock: WorldClock) = viewModelScope.launch { worldClockDao.deleteWorldClock(clock) }
}
