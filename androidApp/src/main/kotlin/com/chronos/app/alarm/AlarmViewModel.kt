package com.chronos.app.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.data.AlarmDao
import com.chronos.app.data.AlarmHistoryDao
import com.chronos.app.data.model.Alarm
import com.chronos.app.data.model.AlarmHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmHistoryDao: AlarmHistoryDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledAlarms: StateFlow<List<Alarm>> = alarmDao.getEnabledAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<AlarmHistory>> = alarmHistoryDao.getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canScheduleExact: Boolean
        get() = alarmScheduler.canScheduleExactAlarms()

    fun createAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val id = alarmDao.insertAlarm(alarm)
            val saved = alarm.copy(id = id)
            alarmScheduler.scheduleAlarm(saved)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val updated = alarm.copy(updatedAt = System.currentTimeMillis())
            alarmDao.updateAlarm(updated)
            alarmScheduler.scheduleAlarm(updated)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            alarmDao.deleteAlarm(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            alarmDao.setAlarmEnabled(alarm.id, enabled)
            if (enabled) {
                alarmScheduler.scheduleAlarm(alarm.copy(isEnabled = true))
            } else {
                alarmScheduler.cancelAlarm(alarm.id)
            }
        }
    }

    fun getNextAlarm(): Flow<Alarm?> {
        return alarmDao.getEnabledAlarms().map { alarms ->
            alarms.minByOrNull { alarm ->
                alarmScheduler.calculateNextTriggerTime(alarm) ?: Long.MAX_VALUE
            }
        }
    }
}
