package com.chronos.app.ui.screens.clock

import androidx.lifecycle.ViewModel
import com.chronos.app.data.TimerDao
import com.chronos.app.data.StopwatchLapDao
import com.chronos.app.data.WorldClockDao
import com.chronos.app.data.model.Timer
import com.chronos.app.data.model.StopwatchLap
import com.chronos.app.data.model.WorldClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ClockViewModel @Inject constructor(
    private val timerDao: TimerDao,
    private val stopwatchLapDao: StopwatchLapDao,
    private val worldClockDao: WorldClockDao
) : ViewModel() {

    val timers: StateFlow<List<Timer>> = timerDao.getAllTimers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val worldClocks: StateFlow<List<WorldClock>> = worldClockDao.getAllWorldClocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getLaps(sessionId: Long): Flow<List<StopwatchLap>> {
        return stopwatchLapDao.getLapsForSession(sessionId)
    }
}
