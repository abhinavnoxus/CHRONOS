package com.chronos.app.ui.screens.alarms

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.chronos.app.alarm.AlarmViewModel
import com.chronos.app.data.model.Alarm
import com.chronos.app.settings.SettingsViewModel
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import java.util.Calendar

private val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
private val dayFlags = listOf(
    Alarm.DAY_MONDAY, Alarm.DAY_TUESDAY, Alarm.DAY_WEDNESDAY,
    Alarm.DAY_THURSDAY, Alarm.DAY_FRIDAY, Alarm.DAY_SATURDAY, Alarm.DAY_SUNDAY
)

@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val palette = LocalChronosPalette.current
    val viewModel: AlarmViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val preferences by settingsViewModel.preferences.collectAsState()
    val alarms by viewModel.alarms.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    var editTarget by remember { mutableStateOf<Alarm?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var resumeCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exactAllowed = remember(resumeCount) { viewModel.canScheduleExact }
    val notificationAllowed = remember(resumeCount) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Alarms", style = MaterialTheme.typography.displaySmall, color = palette.textPrimary)
                Text(
                    text = "${alarms.count { it.isEnabled }} ACTIVE · ${alarms.size} SAVED",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                    color = palette.textTertiary
                )
            }
            IconButton(onClick = { isCreating = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create alarm", tint = palette.accentPrimary)
            }
        }

        if (!exactAllowed || !notificationAllowed) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                style = GlassStyle.FROSTED,
                padding = 14.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = palette.statusWarning)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("Alarm Guard", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        Text(
                            text = when {
                                !notificationAllowed -> "Notifications are off. Alarm controls and status may be hidden."
                                else -> "Exact alarm access is off. Android may deliver alarms late."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    if (!notificationAllowed && Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    } else if (!exactAllowed) {
                        TextButton(onClick = {
                            viewModel.getExactAlarmSettingsIntent()?.let { context.startActivity(it) }
                        }) { Text("Review") }
                    }
                }
            }
        }

        if (alarms.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(16.dp))
                Text("A calmer start begins here", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create an alarm to schedule your next wake-up or reminder. It will be saved on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = { isCreating = true }) { Text("Create an alarm") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        is24Hour = preferences.is24Hour,
                        onOpen = { editTarget = alarm },
                        onToggle = { enabled -> viewModel.toggleAlarm(alarm, enabled) }
                    )
                }
            }
        }

    }

    if (isCreating || editTarget != null) {
        AlarmEditorDialog(
            initial = editTarget,
            is24Hour = preferences.is24Hour,
            onDismiss = { isCreating = false; editTarget = null },
            onSave = { alarm ->
                if (editTarget == null) viewModel.createAlarm(alarm) else viewModel.updateAlarm(alarm)
                isCreating = false
                editTarget = null
            },
            onDelete = { alarm ->
                viewModel.deleteAlarm(alarm)
                editTarget = null
            }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Alarm status") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, is24Hour: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val palette = LocalChronosPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        style = if (alarm.isEnabled) GlassStyle.LUXURY else GlassStyle.SMOKED,
        cornerRadius = 22.dp,
        padding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.formatTime(is24Hour = is24Hour),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                        fontSize = 42.sp
                    ),
                    color = if (alarm.isEnabled) palette.textPrimary else palette.textTertiary
                )
                Text(
                    text = alarm.label.ifBlank { "Alarm" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (alarm.isEnabled) palette.accentPrimary else palette.textTertiary
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = repeatText(alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                if (alarm.isRepeating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) {
                        dayLabels.forEachIndexed { index, label ->
                            val active = alarm.isDayEnabled(index + 1)
                            Surface(
                                shape = CircleShape,
                                color = if (active) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceGlass
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) palette.accentPrimary else palette.textTertiary
                                )
                            }
                        }
                    }
                }
            }
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 8.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.accentPrimary,
                    checkedTrackColor = palette.accentPrimary.copy(alpha = 0.35f),
                    uncheckedThumbColor = palette.textTertiary
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorDialog(
    initial: Alarm?,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalChronosPalette.current
    val now = remember { Calendar.getInstance() }
    var hour by remember(initial?.id) { mutableIntStateOf(initial?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(initial?.id) { mutableIntStateOf(initial?.minute ?: ((now.get(Calendar.MINUTE) + 4) / 5 * 5) % 60) }
    var repeatDays by remember(initial?.id) { mutableIntStateOf(initial?.repeatDays ?: 0) }
    var label by remember(initial?.id) { mutableStateOf(initial?.label.orEmpty()) }
    var ringtoneUri by remember(initial?.id) { mutableStateOf(initial?.ringtoneUri) }
    var vibrate by remember(initial?.id) { mutableStateOf(initial?.vibrate ?: true) }
    var snoozeMinutes by remember(initial?.id) { mutableIntStateOf(initial?.snoozeMinutes ?: 5) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            ringtoneUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New alarm" else "Edit alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                            hour = selectedHour
                            minute = selectedMinute
                        }, hour, minute, is24Hour).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(formatAlarmEditorTime(hour, minute, is24Hour), style = MaterialTheme.typography.headlineSmall)
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(50) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label") },
                    singleLine = true
                )
                Text("REPEAT", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    dayFlags.forEachIndexed { index, flag ->
                        FilterChip(
                            selected = repeatDays and flag != 0,
                            onClick = { repeatDays = repeatDays xor flag },
                            label = { Text(dayLabels[index]) },
                            leadingIcon = if (repeatDays and flag != 0) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Snooze", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { snoozeMinutes = when (snoozeMinutes) { 5 -> 10; 10 -> 15; else -> 5 } }) {
                        Text("${snoozeMinutes} min")
                    }
                }
                OutlinedButton(
                    onClick = {
                        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose alarm sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let(Uri::parse))
                        }
                        ringtonePicker.launch(picker)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (ringtoneUri == null) "System alarm sound" else "Change alarm sound")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    (initial ?: Alarm(hour = hour, minute = minute)).copy(
                        hour = hour,
                        minute = minute,
                        label = label.trim(),
                        repeatDays = repeatDays,
                        ringtoneUri = ringtoneUri,
                        vibrate = vibrate,
                        snoozeMinutes = snoozeMinutes,
                        isEnabled = initial?.isEnabled ?: true,
                        isRinging = false
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    IconButton(onClick = { onDelete(initial) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete alarm", tint = palette.statusError)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun formatAlarmEditorTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    if (is24Hour) return String.format("%02d:%02d", hour, minute)
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    return String.format("%d:%02d %s", displayHour, minute, if (hour < 12) "AM" else "PM")
}

private fun repeatText(alarm: Alarm): String = when {
    !alarm.isRepeating -> "One time"
    alarm.repeatDays == Alarm.EVERY_DAY -> "Every day"
    alarm.repeatDays == Alarm.WEEKDAYS -> "Weekdays"
    alarm.repeatDays == Alarm.WEEKENDS -> "Weekends"
    else -> "Custom schedule"
}
