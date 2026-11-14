package com.chronos.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronos.app.alarm.AlarmViewModel
import com.chronos.app.BuildConfig
import com.chronos.app.alarm.service.AlarmPlaybackService
import com.chronos.app.settings.SettingsViewModel
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle

private data class AccentChoice(val title: String, val argb: Long)
private val accentChoices = listOf(
    AccentChoice("Champagne", 0xFFC6A36BL),
    AccentChoice("Silver", 0xFFA9BDD1L),
    AccentChoice("Sage", 0xFF85AE99L),
    AccentChoice("Rose", 0xFFC98C92L)
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalChronosPalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val alarmViewModel: AlarmViewModel = hiltViewModel()
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    var resumeCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exactAllowed = remember(resumeCount) { alarmViewModel.canScheduleExact }
    val notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                Column {
                    Text("Personalize", style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary)
                    Text("A quieter, clearer view of time", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
        }
        item {
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                SettingLabel("Theme")
                ChoiceRow(listOf("dark" to "Obsidian", "light" to "Ivory", "adaptive" to "Adaptive"), preferences.themeMode) {
                    settingsViewModel.setThemeMode(it)
                }
                Spacer(Modifier.height(10.dp))
                SettingLabel("Glass surface")
                ChoiceRow(listOf("luxury" to "Luxury", "crystal" to "Crystal", "smoked" to "Smoked", "frosted" to "Frosted", "amoled" to "AMOLED"), preferences.glassStyle) {
                    settingsViewModel.setGlassStyle(it)
                }
                Spacer(Modifier.height(10.dp))
                SettingLabel("Accent colour")
                ChoiceRow(accentChoices.map { it.argb.toString() to it.title }, preferences.accentArgb.toString()) { selected ->
                    selected.toLongOrNull()?.let(settingsViewModel::setAccentArgb)
                }
            }
        }
        item {
            SettingsSection(title = "Clock", icon = Icons.Default.Schedule) {
                SettingToggle("24-hour time", "Use a 24-hour display", preferences.is24Hour, settingsViewModel::set24Hour)
                SettingToggle("Show seconds", "Update the clock once per second", preferences.showSeconds, settingsViewModel::setShowSeconds)
                SettingToggle("Show date", "Display today's date on Home", preferences.showDate, settingsViewModel::setShowDate)
                Spacer(Modifier.height(8.dp))
                SettingLabel("Clock face")
                ChoiceRow(listOf("digital" to "Digital", "analog" to "Analog", "hybrid" to "Hybrid"), preferences.clockStyle) {
                    settingsViewModel.setClockStyle(it)
                }
            }
        }
        item {
            SettingsSection(title = "Motion & performance", icon = Icons.Default.Security) {
                SettingLabel("Motion")
                ChoiceRow(listOf("cinematic" to "Cinematic", "balanced" to "Balanced", "reduced" to "Reduced"), preferences.animationIntensity) {
                    settingsViewModel.setAnimationIntensity(it)
                }
                Spacer(Modifier.height(10.dp))
                SettingLabel("Visual effects")
                ChoiceRow(listOf("full" to "Full", "balanced" to "Balanced", "saver" to "Saver"), preferences.performanceMode) {
                    settingsViewModel.setPerformanceMode(it)
                }
            }
        }
        item {
            SettingsSection(title = "Home layout", icon = Icons.Default.Schedule) {
                SettingToggle("Upcoming agenda", "Show calendar events on Home", preferences.showAgenda, settingsViewModel::setShowAgenda)
                SettingToggle("Reminders", "Show your next reminders on Home", preferences.showReminders, settingsViewModel::setShowReminders)
                SettingToggle("Quick actions", "Show navigation shortcuts", preferences.showQuickActions, settingsViewModel::setShowQuickActions)
                SettingToggle("Haptic feedback", "Use subtle feedback for primary controls", preferences.hapticFeedback, settingsViewModel::setHapticFeedback)
            }
        }
        item {
            SettingsSection(title = "Focus defaults", icon = Icons.Default.Schedule) {
                SettingLabel("Focus length")
                ChoiceRow(listOf(25, 45, 60, 90).map { it.toString() to "$it min" }, preferences.defaultFocusMinutes.toString()) {
                    settingsViewModel.setFocusDefaults(it.toInt(), preferences.defaultBreakMinutes, preferences.defaultLongBreakMinutes, preferences.sessionsBeforeLongBreak)
                }
                SettingLabel("Short break")
                ChoiceRow(listOf(5, 10, 15).map { it.toString() to "$it min" }, preferences.defaultBreakMinutes.toString()) {
                    settingsViewModel.setFocusDefaults(preferences.defaultFocusMinutes, it.toInt(), preferences.defaultLongBreakMinutes, preferences.sessionsBeforeLongBreak)
                }
                SettingLabel("Long break")
                ChoiceRow(listOf(15, 20, 30).map { it.toString() to "$it min" }, preferences.defaultLongBreakMinutes.toString()) {
                    settingsViewModel.setFocusDefaults(preferences.defaultFocusMinutes, preferences.defaultBreakMinutes, it.toInt(), preferences.sessionsBeforeLongBreak)
                }
                SettingLabel("Intervals before a long break")
                ChoiceRow(listOf(2, 3, 4, 6).map { it.toString() to it }, preferences.sessionsBeforeLongBreak.toString()) {
                    settingsViewModel.setFocusDefaults(preferences.defaultFocusMinutes, preferences.defaultBreakMinutes, preferences.defaultLongBreakMinutes, it.toInt())
                }
            }
        }
        item {
            SettingsSection(title = "Alarm Guard", icon = Icons.Default.Security) {
                Text(
                    text = when {
                        !notificationsAllowed -> "Notifications are disabled. Ringing alarms still use system playback where allowed, but notification controls may not appear."
                        !exactAllowed -> "Exact-alarm access is unavailable. Scheduled alarms may be delayed by Android."
                        else -> "Exact-alarm access is available. Android power settings can still affect background delivery."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (exactAllowed && notificationsAllowed) palette.statusSuccess else palette.statusWarning
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAllowed) {
                        OutlinedButton(onClick = {
                            alarmViewModel.getExactAlarmSettingsIntent()?.let { context.startActivity(it) }
                        }) { Text("Exact alarms") }
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, AlarmPlaybackService.NOTIFICATION_CHANNEL_ID)
                        })
                    }) { Text("Alarm channel") }
                }
                Text(
                    text = "On Realme UI, allow CHRONOS to auto-launch and use background activity if alarms are delayed. These settings vary by OS version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        item {
            SettingsSection(title = "About", icon = Icons.Default.Security) {
                Text("CHRONOS ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                Text("Personal time tools stored on this device.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalChronosPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        style = GlassStyle.LUXURY,
        cornerRadius = 22.dp,
        padding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    val palette = LocalChronosPalette.current
    Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = palette.textTertiary)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    choices: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        choices.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalChronosPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
