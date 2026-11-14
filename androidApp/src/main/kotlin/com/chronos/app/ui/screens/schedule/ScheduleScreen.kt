package com.chronos.app.ui.screens.schedule

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.chronos.app.calendar.CalendarSyncManager
import com.chronos.app.calendar.CalendarViewModel
import com.chronos.app.data.model.CalendarEvent
import com.chronos.app.settings.SettingsViewModel
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val palette = LocalChronosPalette.current
    val viewModel: CalendarViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val preferences by settingsViewModel.preferences.collectAsState()
    val events by viewModel.events.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var resumeCount by remember { mutableIntStateOf(0) }
    var askedForPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasCalendarPermission = remember(resumeCount) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedForPermission = true
        if (granted) viewModel.refresh()
    }
    LaunchedEffect(hasCalendarPermission) {
        if (hasCalendarPermission) viewModel.refresh()
    }

    val todayStart = remember(resumeCount) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayEnd = todayStart + 24L * 60L * 60L * 1_000L
    val todayEvents = remember(events, todayStart) {
        events.filter { it.startTime < todayEnd && it.endTime >= todayStart }
    }
    val conflicts = remember(todayEvents) { CalendarSyncManager(context).detectConflicts(todayEvents) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Plan", style = MaterialTheme.typography.displaySmall, color = palette.textPrimary)
                    Text(
                        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = palette.textTertiary
                    )
                }
                if (hasCalendarPermission) {
                    IconButton(onClick = viewModel::refresh, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh calendar", tint = palette.textSecondary)
                    }
                }
            }
        }
        if (!hasCalendarPermission) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.FROSTED, cornerRadius = 24.dp) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Your schedule, at a glance", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "CHRONOS reads events from calendars already on your phone. Access is optional and read-only; alarms remain available without it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                        Text(if (askedForPermission) "Try calendar access again" else "Connect device calendar")
                    }
                }
            }
        } else {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.SMOKED, padding = 16.dp) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        SummaryValue("TODAY", todayEvents.size.toString(), palette.accentPrimary)
                        SummaryValue("CONFLICTS", conflicts.size.toString(), if (conflicts.isEmpty()) palette.statusSuccess else palette.statusWarning)
                        SummaryValue("NEXT 7 DAYS", events.size.toString(), palette.textPrimary)
                    }
                }
            }
            if (loading && events.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = palette.accentPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("Reading device calendars…", color = palette.textSecondary)
                    }
                }
            } else if (error != null) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.FROSTED) {
                        Text("Calendar unavailable", style = MaterialTheme.typography.titleMedium, color = palette.statusWarning)
                        Spacer(Modifier.height(6.dp))
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                        TextButtonRefresh(onClick = viewModel::refresh)
                    }
                }
            } else if (events.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.LUXURY) {
                        Text("Nothing scheduled", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("There are no upcoming events in the calendars available on this device.", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    }
                }
            } else {
                item {
                    Text("UPCOMING EVENTS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp), color = palette.textTertiary)
                }
                items(events, key = { "${it.calendarId}:${it.eventId}:${it.startTime}" }) { event ->
                    EventCard(event, is24Hour = preferences.is24Hour) {
                        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setData(eventUri)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    val palette = LocalChronosPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = tint, fontWeight = FontWeight.Light)
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
    }
}

@Composable
private fun EventCard(event: CalendarEvent, is24Hour: Boolean, onOpen: () -> Unit) {
    val palette = LocalChronosPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        style = GlassStyle.LUXURY,
        cornerRadius = 20.dp,
        padding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.padding(end = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (event.allDay) "ALL" else formatEventTime(event.startTime, is24Hour),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accentPrimary
                )
                if (!event.allDay) {
                    Text(formatEventTime(event.endTime, is24Hour), style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                val detail = listOf(event.location, if (event.isRecurring) "Recurring" else "", event.calendarName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                if (event.description.isNotBlank()) {
                    Text(event.description, style = MaterialTheme.typography.bodySmall, color = palette.textTertiary, maxLines = 2)
                }
            }
        }
    }
}

private fun formatEventTime(timestamp: Long, is24Hour: Boolean): String =
    SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun TextButtonRefresh(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) {
        Text("Try again")
    }
}
