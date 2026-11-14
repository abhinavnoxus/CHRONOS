package com.chronos.app.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.model.Reminder
import com.chronos.app.data.model.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(private val repository: ReminderRepository) : ViewModel() {
    val reminders = repository.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routines = repository.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    fun saveReminder(reminder: Reminder) = run {
        viewModelScope.launch { try { repository.saveReminder(reminder) } catch (e: Exception) { _error.value = e.message ?: "Could not save reminder" } }
    }
    fun complete(id: Long) { viewModelScope.launch { runCatching { repository.completeReminder(id) }.onFailure { _error.value = "Could not complete reminder" } } }
    fun deleteReminder(reminder: Reminder) { viewModelScope.launch { runCatching { repository.deleteReminder(reminder) }.onFailure { _error.value = "Could not delete reminder" } } }
    fun saveRoutine(routine: Routine) {
        viewModelScope.launch { try { repository.saveRoutine(routine) } catch (e: Exception) { _error.value = e.message ?: "Could not save routine" } }
    }
    fun deleteRoutine(id: Long) { viewModelScope.launch { runCatching { repository.deleteRoutine(id) }.onFailure { _error.value = "Could not delete routine" } } }
}
