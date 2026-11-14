package com.chronos.app.ui.screens.plan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.data.model.Reminder
import com.chronos.app.data.model.Routine
import com.chronos.app.reminders.ReminderViewModel
import com.chronos.app.settings.SettingsViewModel
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.ScreenHeader
import com.chronos.app.ui.components.ScreenPadding
import com.chronos.app.ui.components.SectionLabel
import com.chronos.app.ui.components.SectionSpacing
import com.chronos.app.ui.components.StatePanel
import com.chronos.app.ui.screens.schedule.ScheduleScreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val palette = LocalChronosPalette.current
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = palette.backgroundPrimary, contentColor = palette.accentPrimary) {
            listOf("Agenda", "Reminders", "Routines").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) { 1 -> RemindersPane(); 2 -> RoutinesPane(); else -> ScheduleScreen() }
    }
}

@Composable
private fun RemindersPane() {
    val viewModel: ReminderViewModel = hiltViewModel()
    val settings: SettingsViewModel = hiltViewModel()
    val items by viewModel.reminders.collectAsStateWithLifecycle()
    val prefs by settings.preferences.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val palette = LocalChronosPalette.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Reminder?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item {
            ScreenHeader("Reminders", "A small nudge, right on time", action = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Create reminder", tint = palette.accentPrimary) }
            })
        }
        if (error != null) item { Text(error.orEmpty(), color = palette.statusError); TextButton(onClick = viewModel::clearError) { Text("Dismiss") } }
        if (items.isEmpty()) item {
            StatePanel("Nothing to remember yet", "Add a one-off reminder or a repeating interval. Notifications are local and never modify your calendar.", Icons.Default.Notifications, actionLabel = "Add reminder", onAction = { creating = true })
        }
        items(items, key = { it.id }) { item ->
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = if (item.isCompleted) palette.textTertiary else palette.textPrimary)
                        Text(formatDateTime(item.scheduledTime, prefs.is24Hour) + if (item.isRecurring) " · repeats" else if (item.isCompleted) " · done" else "",
                            style = MaterialTheme.typography.bodySmall, color = palette.accentPrimary)
                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                    if (!item.isCompleted) IconButton(onClick = { viewModel.complete(item.id) }) { Icon(Icons.Default.Check, "Mark done", tint = palette.statusSuccess) }
                    IconButton(onClick = { editing = item }) { Icon(Icons.Default.Notifications, "Edit reminder", tint = palette.textSecondary) }
                    IconButton(onClick = { deleting = item }) { Icon(Icons.Default.Delete, "Delete reminder", tint = palette.textTertiary) }
                }
                androidx.compose.material3.HorizontalDivider(color = palette.borderSubtle)
            }
        }
    }
    if (creating || editing != null) ReminderEditor(editing, prefs.is24Hour, { creating = false; editing = null }, {
        viewModel.saveReminder(it); creating = false; editing = null
    })
    deleting?.let { ConfirmDelete(it.title, { deleting = null }, { viewModel.deleteReminder(it); deleting = null }) }
}

@Composable
private fun RoutinesPane() {
    val viewModel: ReminderViewModel = hiltViewModel()
    val settings: SettingsViewModel = hiltViewModel()
    val items by viewModel.routines.collectAsStateWithLifecycle()
    val prefs by settings.preferences.collectAsStateWithLifecycle()
    val palette = LocalChronosPalette.current
    val error by viewModel.error.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var deleting by remember { mutableStateOf<Routine?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item { ScreenHeader("Routines", "Make the important things repeat", action = {
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Create routine", tint = palette.accentPrimary) }
        }) }
        if (error != null) item { Text(error.orEmpty(), color = palette.statusError) }
        if (items.isEmpty()) item { StatePanel("Build your rhythm", "Create a weekday routine such as a morning review or an evening shutdown. CHRONOS sends a reminder, not an audible alarm.", Icons.Default.Repeat, actionLabel = "Create routine", onAction = { creating = true }) }
        items(items, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", item.hour, item.minute), style = MaterialTheme.typography.headlineMedium, color = palette.accentPrimary)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(weekdayText(item.repeatDays) + if (!item.isEnabled) " · paused" else "", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                IconButton(onClick = { editing = item }) { Icon(Icons.Default.Repeat, "Edit routine", tint = palette.textSecondary) }
                IconButton(onClick = { deleting = item }) { Icon(Icons.Default.Delete, "Delete routine", tint = palette.textTertiary) }
            }
        }
    }
    if (creating || editing != null) RoutineEditor(editing, prefs.is24Hour, { creating = false; editing = null }, {
        viewModel.saveRoutine(it); creating = false; editing = null
    })
    deleting?.let { ConfirmDelete(it.title, { deleting = null }, { viewModel.deleteRoutine(it.id); deleting = null }) }
}

@Composable
private fun ReminderEditor(initial: Reminder?, is24Hour: Boolean, onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.description.orEmpty()) }
    var at by remember { mutableLongStateOf(initial?.scheduledTime ?: (System.currentTimeMillis() + 3_600_000L)) }
    var recurring by remember { mutableStateOf(initial?.isRecurring ?: false) }
    var interval by remember { mutableStateOf(initial?.repeatInterval?.takeIf { it > 0 } ?: 86_400_000L) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "New reminder" else "Edit reminder") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(note, { note = it.take(300) }, label = { Text("Note (optional)") }, maxLines = 3)
            DateTimeButton(at, is24Hour) { at = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Repeat", Modifier.weight(1f)); androidx.compose.material3.Switch(recurring, { recurring = it }) }
            if (recurring) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3_600_000L to "Hour", 86_400_000L to "Day", 604_800_000L to "Week").forEach { (value, label) ->
                    FilterChip(interval == value, { interval = value }, label = { Text(label) })
                }
            }
            if (at <= System.currentTimeMillis()) Text("Choose a future time to schedule delivery.", color = LocalChronosPalette.current.statusWarning, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && at > System.currentTimeMillis(), onClick = {
        onSave((initial ?: Reminder(title = title, scheduledTime = at)).copy(title = title, description = note, scheduledTime = at,
            isRecurring = recurring, repeatInterval = if (recurring) interval else 0L, isCompleted = false, completedAt = null))
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RoutineEditor(initial: Routine?, is24Hour: Boolean, onDismiss: () -> Unit, onSave: (Routine) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var hour by remember { mutableIntStateOf(initial?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(initial?.minute ?: 0) }
    var days by remember { mutableIntStateOf(initial?.repeatDays ?: 31) }
    var enabled by remember { mutableStateOf(initial?.isEnabled ?: true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "New routine" else "Edit routine") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Routine name") }, singleLine = true)
            OutlinedTextField(note, { note = it.take(300) }, label = { Text("Note (optional)") }, maxLines = 2)
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, is24Hour).show() }) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute), style = MaterialTheme.typography.headlineSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                    val bit = 1 shl index
                    FilterChip(days and bit != 0, { days = days xor bit }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); androidx.compose.material3.Switch(enabled, { enabled = it }) }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && days != 0, onClick = {
        onSave((initial ?: Routine(title = title, hour = hour, minute = minute)).copy(title = title, note = note, hour = hour, minute = minute, repeatDays = days, isEnabled = enabled))
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DateTimeButton(at: Long, is24Hour: Boolean, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val calendar = Calendar.getInstance().apply { timeInMillis = at }
        DatePickerDialog(context, { _, y, m, d ->
            calendar.set(y, m, d)
            TimePickerDialog(context, { _, h, min -> calendar.set(Calendar.HOUR_OF_DAY, h); calendar.set(Calendar.MINUTE, min); calendar.set(Calendar.SECOND, 0); onChange(calendar.timeInMillis) },
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), is24Hour).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }) { Text(formatDateTime(at, is24Hour)) }
}

@Composable
private fun ConfirmDelete(title: String, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text("Delete this item?") }, text = { Text("“$title” and its scheduled notification will be removed.") },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete", color = LocalChronosPalette.current.statusError) } },
        dismissButton = { TextButton(onClick = cancel) { Text("Keep") } })
}

private fun formatDateTime(at: Long, is24Hour: Boolean): String = SimpleDateFormat("EEE, MMM d · " + if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(at))
private fun weekdayText(mask: Int): String = when (mask) { 127 -> "Every day"; 31 -> "Weekdays"; 96 -> "Weekends"; else -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(" · ") }
