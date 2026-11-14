package com.chronos.app.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.AlarmHistoryDao
import com.chronos.app.data.FocusSessionDao
import com.chronos.app.data.model.AlarmHistory
import com.chronos.app.data.model.FocusSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    focusSessionDao: FocusSessionDao,
    alarmHistoryDao: AlarmHistoryDao,
    reminderDao: com.chronos.app.data.ReminderDao,
    calendarEventDao: com.chronos.app.data.CalendarEventDao
) : ViewModel() {
    val focusSessions: StateFlow<List<FocusSession>> = focusSessionDao.getSessionsSince(System.currentTimeMillis() - 30L * 86_400_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alarmHistory: StateFlow<List<AlarmHistory>> = alarmHistoryDao.getHistorySince(
        System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1_000L
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders = reminderDao.getAllReminders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val events = calendarEventDao.getAllEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
