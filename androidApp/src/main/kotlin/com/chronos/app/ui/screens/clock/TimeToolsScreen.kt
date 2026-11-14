package com.chronos.app.ui.screens.clock

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.data.model.Timer
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import com.chronos.app.ui.components.ScreenHeader
import com.chronos.app.ui.components.ScreenPadding
import com.chronos.app.ui.components.SectionSpacing
import com.chronos.app.ui.components.StatePanel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeToolsScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val palette = LocalChronosPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Home") }
            Text("Clock studio", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        }
        TabRow(selectedTabIndex = tab, containerColor = palette.backgroundPrimary, contentColor = palette.accentPrimary) {
            listOf("Timers", "Stopwatch", "World").forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
        }
        when (tab) { 1 -> StopwatchPane(); 2 -> WorldClockPane(); else -> TimersPane() }
    }
}

@Composable
private fun TimersPane() {
    val viewModel: TimeToolsViewModel = hiltViewModel()
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val palette = LocalChronosPalette.current
    var creating by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500L) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item { ScreenHeader("Countdowns", "Keep a little time in reserve", action = {
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Create timer", tint = palette.accentPrimary) }
        }) }
        if (error != null) item { Text(error.orEmpty(), color = palette.statusError) }
        if (timers.isEmpty()) item { StatePanel("No running countdowns", "Start a timer for tea, a work sprint, or anything in between. Completion is scheduled with Android, even when this screen is closed.", Icons.Default.Timer, actionLabel = "Start a timer", onAction = { creating = true }) }
        items(timers, key = { it.id }) { timer ->
            GlassCard(Modifier.fillMaxWidth(), style = GlassStyle.LUXURY, cornerRadius = 24.dp, padding = 20.dp) {
                Text(timer.label.ifBlank { "Timer" }, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                val remaining = viewModel.remaining(timer, now)
                Text(formatDuration(remaining), style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light), color = palette.accentPrimary)
                Text(if (remaining == 0L) "Complete" else if (timer.isRunning) "Running · ${formatDuration(timer.totalDuration)} total" else "Paused", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remaining > 0L) OutlinedButton(onClick = { if (timer.isRunning) viewModel.pauseTimer(timer) else viewModel.resumeTimer(timer) }) {
                        Icon(if (timer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, if (timer.isRunning) "Pause timer" else "Resume timer")
                    }
                    IconButton(onClick = { viewModel.resetTimer(timer) }) { Icon(Icons.Default.Refresh, "Reset timer", tint = palette.textSecondary) }
                    IconButton(onClick = { viewModel.deleteTimer(timer) }) { Icon(Icons.Default.Delete, "Delete timer", tint = palette.textTertiary) }
                }
            }
        }
    }
    if (creating) TimerEditor({ creating = false }, { label, minutes -> viewModel.startTimer(label, minutes); creating = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerEditor(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(10) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New countdown") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(label, { label = it.take(60) }, label = { Text("Label (optional)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 25, 60).forEach { value -> FilterChip(minutes == value, { minutes = value }, label = { Text("${value}m") }) } }
            OutlinedTextField(minutes.toString(), { minutes = it.toIntOrNull()?.coerceIn(1, 1440) ?: 1 }, label = { Text("Minutes") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { onSave(label, minutes) }) { Text("Start") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopwatchPane() {
    val viewModel: TimeToolsViewModel = hiltViewModel()
    val state by viewModel.stopwatch.collectAsStateWithLifecycle()
    val palette = LocalChronosPalette.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.running) { while (state.running) { now = SystemClock.elapsedRealtime(); delay(50L) } }
    val elapsed = state.elapsed(now)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item { ScreenHeader("Stopwatch", "Measure the moment") }
        item { GlassCard(Modifier.fillMaxWidth(), style = GlassStyle.LUXURY, cornerRadius = 26.dp, padding = 24.dp) {
            Text(formatStopwatch(elapsed), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 46.sp), color = palette.textPrimary)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::toggleStopwatch, modifier = Modifier.weight(1f)) { Text(if (state.running) "Pause" else if (elapsed > 0L) "Resume" else "Start") }
                OutlinedButton(onClick = viewModel::lap, enabled = state.running, modifier = Modifier.weight(1f)) { Text("Lap") }
                TextButton(onClick = viewModel::resetStopwatch, enabled = elapsed > 0L && !state.running) { Text("Reset") }
            }
        } }
        items(state.laps, key = { it.lapNumber }) { lap ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Lap ${lap.lapNumber}", Modifier.weight(1f), color = palette.textSecondary)
                Text(formatStopwatch(lap.lapTime), color = palette.accentPrimary, fontFamily = FontFamily.Monospace)
                Text(formatStopwatch(lap.totalTime), Modifier.padding(start = 20.dp), color = palette.textTertiary, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun WorldClockPane() {
    val viewModel: TimeToolsViewModel = hiltViewModel()
    val clocks by viewModel.worldClocks.collectAsStateWithLifecycle()
    val palette = LocalChronosPalette.current
    var adding by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000L) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item { ScreenHeader("World time", "The same moment, elsewhere", action = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add world clock", tint = palette.accentPrimary) }
        }) }
        if (clocks.isEmpty()) item { StatePanel("Your local world", "Add cities to compare time zones. Daylight-saving offsets are resolved by Android's time-zone database.", Icons.Default.Public, actionLabel = "Add a city", onAction = { adding = true }) }
        items(clocks, key = { it.id }) { clock ->
            val zone = TimeZone.getTimeZone(clock.timeZoneId)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clock.city, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(SimpleDateFormat("EEE, MMM d · z", Locale.getDefault()).apply { timeZone = zone }.format(Date(now)), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                Text(formatter.format(Date(now)), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace), color = palette.accentPrimary)
                IconButton(onClick = { viewModel.removeWorldClock(clock) }) { Icon(Icons.Default.Delete, "Remove ${clock.city}", tint = palette.textTertiary) }
            }
        }
    }
    if (adding) CityPicker({ adding = false }, { city, zone -> viewModel.addWorldClock(city, zone); adding = false })
}

@Composable
private fun CityPicker(onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val choices = remember(query) {
        TimeZone.getAvailableIDs().filter { it.contains('/') && !it.startsWith("Etc/") }
            .filter { it.replace('_', ' ').contains(query.trim(), ignoreCase = true) }.sorted().take(40)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a city") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Search city or time zone") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.height(280.dp)) {
                items(choices) { zone -> TextButton(onClick = { onPick(zone.substringAfterLast('/').replace('_', ' '), zone) }, modifier = Modifier.fillMaxWidth()) { Text(zone.replace('_', ' '), modifier = Modifier.fillMaxWidth()) } }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis + 999L) / 1000L
    return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
}
private fun formatStopwatch(millis: Long): String = String.format(Locale.getDefault(), "%02d:%02d.%02d", millis / 60000, millis / 1000 % 60, millis / 10 % 100)
