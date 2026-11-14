package com.chronos.app.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.CalendarEventDao
import com.chronos.app.data.model.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarSyncManager: CalendarSyncManager,
    private val calendarEventDao: CalendarEventDao,
    private val database: com.chronos.app.data.ChronosDatabase
) : ViewModel() {
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val fetched = withContext(Dispatchers.IO) { calendarSyncManager.fetchUpcomingEvents() }
                androidx.room.withTransaction(database) {
                    calendarEventDao.deleteAll()
                    if (fetched.isNotEmpty()) calendarEventDao.insertAll(fetched)
                }
                _events.value = fetched.sortedBy { it.startTime }
            } catch (exception: SecurityException) {
                _error.value = "Calendar access was denied. Your alarms and other CHRONOS features still work."
                Log.w(TAG, "Calendar permission was revoked", exception)
            } catch (exception: Exception) {
                _error.value = "Could not load calendar events. Try again after device calendars finish syncing."
                Log.e(TAG, "Calendar refresh failed", exception)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        private const val TAG = "CalendarViewModel"
    }
}
