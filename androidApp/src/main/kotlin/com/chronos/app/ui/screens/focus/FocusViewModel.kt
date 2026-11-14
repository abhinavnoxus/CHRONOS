package com.chronos.app.ui.screens.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.FocusSessionDao
import com.chronos.app.settings.ChronosPreferences
import com.chronos.app.settings.ChronosSettings
import com.chronos.app.settings.FocusRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val engine: FocusEngine,
    private val clock: FocusClock,
    focusSessionDao: FocusSessionDao,
    settings: ChronosSettings
) : ViewModel() {
    val runtime: StateFlow<FocusRuntime> = engine.runtime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusRuntime())
    val preferences = settings.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChronosPreferences.Default)
    val history = focusSessionDao.getAllSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { reconcile() }
    fun remainingMillis(runtime: FocusRuntime = this.runtime.value): Long = engine.remaining(runtime, clock.elapsedMillis(), clock.bootCount())
    fun start(label: String, focusMinutes: Int) {
        val prefs = preferences.value
        viewModelScope.launch { engine.start(label, focusMinutes, prefs.defaultBreakMinutes, prefs.defaultLongBreakMinutes, prefs.sessionsBeforeLongBreak) }
    }
    fun pause() { viewModelScope.launch { engine.pause() } }
    fun resume() { viewModelScope.launch { engine.resume() } }
    fun stop() { viewModelScope.launch { engine.stop() } }
    fun onTimerTick() = reconcile()
    fun reconcile() { viewModelScope.launch { engine.reconcile(clock.wallMillis(), clock.elapsedMillis(), clock.bootCount()) } }

    companion object {
        const val PHASE_FOCUS = "focus"
        const val PHASE_BREAK = "break"
        const val PHASE_LONG_BREAK = "long_break"
    }
}
