package com.chronos.app.alarm

import android.util.Log
import android.content.Context
import com.chronos.app.widget.ChronosWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.data.AlarmDao
import com.chronos.app.data.AlarmHistoryDao
import com.chronos.app.data.model.Alarm
import com.chronos.app.data.model.AlarmHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val alarmHistoryDao: AlarmHistoryDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enabledAlarms: StateFlow<List<Alarm>> = alarmDao.getEnabledAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentHistory: StateFlow<List<AlarmHistory>> = alarmHistoryDao.getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val canScheduleExact: Boolean
        get() = alarmScheduler.canScheduleExactAlarms()

    fun getExactAlarmSettingsIntent() = alarmScheduler.getExactAlarmSettingsIntent()

    fun clearError() {
        _errorMessage.value = null
    }

    fun createAlarm(alarm: Alarm) {
        viewModelScope.launch {
            try {
                val id = alarmDao.insertAlarm(alarm.copy(id = 0L, isRinging = false))
                val saved = alarm.copy(id = id, isRinging = false)
                ChronosWidgetProvider.requestUpdate(context)
                if (saved.isEnabled && !alarmScheduler.scheduleAlarm(saved)) {
                    _errorMessage.value = "The alarm was saved, but Android did not accept its schedule. Check Alarm Guard."
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not create alarm", exception)
                _errorMessage.value = "Could not save this alarm. Your existing alarms were not changed."
            }
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            try {
                val updated = alarm.copy(updatedAt = System.currentTimeMillis())
                alarmDao.updateAlarm(updated)
                ChronosWidgetProvider.requestUpdate(context)
                if (updated.isEnabled) {
                    if (!alarmScheduler.scheduleAlarm(updated)) {
                        _errorMessage.value = "The alarm was saved, but Android did not accept its schedule. Check Alarm Guard."
                    }
                } else {
                    alarmScheduler.cancelAlarm(updated.id)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not update alarm ${alarm.id}", exception)
                _errorMessage.value = "Could not update this alarm."
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            try {
                alarmScheduler.cancelAlarm(alarm.id)
                alarmDao.deleteAlarm(alarm)
                ChronosWidgetProvider.requestUpdate(context)
            } catch (exception: Exception) {
                Log.e(TAG, "Could not delete alarm ${alarm.id}", exception)
                _errorMessage.value = "Could not delete this alarm."
            }
        }
    }

    fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            try {
                alarmDao.setAlarmEnabled(alarm.id, enabled)
                ChronosWidgetProvider.requestUpdate(context)
                if (enabled) {
                    if (!alarmScheduler.scheduleAlarm(alarm.copy(isEnabled = true))) {
                        _errorMessage.value = "The alarm is on, but Android did not accept its schedule. Check Alarm Guard."
                    }
                } else {
                    alarmScheduler.cancelAlarm(alarm.id)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not toggle alarm ${alarm.id}", exception)
                _errorMessage.value = "Could not change this alarm."
            }
        }
    }

    fun getNextAlarm() = enabledAlarms.map { items ->
        items.mapNotNull { alarm ->
            alarmScheduler.calculateNextTriggerTime(alarm)?.let { triggerAt -> alarm to triggerAt }
        }.minByOrNull { it.second }
    }

    companion object {
        private const val TAG = "AlarmViewModel"
    }
}
