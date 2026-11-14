package com.chronos.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: ChronosSettings
) : ViewModel() {
    val preferences: StateFlow<ChronosPreferences> = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChronosPreferences.Default)

    fun setThemeMode(value: String) { viewModelScope.launch { settings.setThemeMode(value) } }
    fun setGlassStyle(value: String) { viewModelScope.launch { settings.setGlassStyle(value) } }
    fun setAccentArgb(value: Long) { viewModelScope.launch { settings.setAccentArgb(value) } }
    fun setAnimationIntensity(value: String) { viewModelScope.launch { settings.setAnimationIntensity(value) } }
    fun setPerformanceMode(value: String) { viewModelScope.launch { settings.setPerformanceMode(value) } }
    fun set24Hour(value: Boolean) { viewModelScope.launch { settings.set24Hour(value) } }
    fun setShowSeconds(value: Boolean) { viewModelScope.launch { settings.setShowSeconds(value) } }
    fun setShowDate(value: Boolean) { viewModelScope.launch { settings.setShowDate(value) } }
    fun setClockStyle(value: String) { viewModelScope.launch { settings.setClockStyle(value) } }
    fun setHapticFeedback(value: Boolean) { viewModelScope.launch { settings.setHapticFeedback(value) } }
}
