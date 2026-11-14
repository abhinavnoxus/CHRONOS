package com.chronos.app.ui.screens.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.StopwatchLapDao
import com.chronos.app.data.TimerDao
import com.chronos.app.data.WorldClockDao
import com.chronos.app.data.model.StopwatchLap
import com.chronos.app.data.model.Timer
import com.chronos.app.data.model.WorldClock
import com.chronos.app.settings.ChronosPreferences
import com.chronos.app.settings.ChronosSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ClockViewModel @Inject constructor(
    private val timerDao: TimerDao,
    private val stopwatchLapDao: StopwatchLapDao,
    private val worldClockDao: WorldClockDao,
    settings: ChronosSettings
) : ViewModel() {
    val timers: StateFlow<List<Timer>> = timerDao.getAllTimers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val worldClocks: StateFlow<List<WorldClock>> = worldClockDao.getAllWorldClocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferences: StateFlow<ChronosPreferences> = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChronosPreferences.Default)

    fun getLaps(sessionId: Long): Flow<List<StopwatchLap>> = stopwatchLapDao.getLapsForSession(sessionId)
}
