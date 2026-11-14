package com.chronos.app.ui.screens.insights

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronos.app.data.model.FocusSession
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DailyFocus(val start: Long, val label: String, val minutes: Int)

@Composable
fun InsightsScreen() {
    val palette = LocalChronosPalette.current
    val viewModel: InsightsViewModel = hiltViewModel()
    val sessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val alarmHistory by viewModel.alarmHistory.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }
    val weekStart = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
    }
    val recordedSessions = remember(sessions, weekStart) {
        sessions.filter { it.endedAt != null && it.startedAt >= weekStart }
    }
    val totalMinutes = recordedSessions.sumOf { it.totalFocusMinutes }
    val completedIntervals = recordedSessions.sumOf { it.completedSessions }
    val dismissedAlarms = alarmHistory.count { it.timestamp >= weekStart && it.action == "dismissed" }
    val days = remember(recordedSessions, weekStart) { buildDailyFocus(recordedSessions, weekStart) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("insights-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Insights", style = MaterialTheme.typography.displaySmall, color = palette.textPrimary)
                Text("Recorded activity · Last 7 days", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("FOCUS TIME", formatDuration(totalMinutes), Icons.Default.Schedule, Modifier.weight(1f))
                MetricCard("SESSIONS", completedIntervals.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
            }
        }
        item {
            MetricCard("ALARMS DISMISSED", dismissedAlarms.toString(), Icons.Default.AutoGraph, Modifier.fillMaxWidth())
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.LUXURY, cornerRadius = 22.dp) {
                Text("Focus rhythm", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                Text("Minutes recorded in completed focus sessions", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Spacer(Modifier.height(16.dp))
                WeeklyFocusChart(days)
            }
        }
        if (recordedSessions.isEmpty() && dismissedAlarms == 0) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.SMOKED, cornerRadius = 20.dp) {
                    Text("Insights grow with your activity", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Start a Focus session or use an alarm. CHRONOS reports only activity it records; calendar time is planned time, not a measurement of time spent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), style = GlassStyle.SMOKED, cornerRadius = 22.dp) {
                Text("Your time pattern", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                Spacer(Modifier.height(10.dp))
                Text("${reminders.count { it.isCompleted }} reminders completed · ${reminders.count { !it.isCompleted }} still open", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                val busiest = events.filter { !it.allDay }.groupBy { event -> java.util.Calendar.getInstance().apply { timeInMillis = event.startTime }.get(java.util.Calendar.HOUR_OF_DAY) }.maxByOrNull { it.value.size }
                Text(if (busiest == null) "Connect calendar access in Plan to see schedule patterns." else "Your cached schedule is busiest around ${String.format("%02d:00", busiest.key)} (${busiest.value.size} events in the cached week).", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                Spacer(Modifier.height(10.dp))
                Text("Suggestion · no automatic changes", style = MaterialTheme.typography.labelMedium, color = palette.accentPrimary)
                Text(if (totalMinutes == 0) "Try one short focus interval. Nothing is scheduled or changed until you choose it." else if (reminders.any { !it.isCompleted && it.scheduledTime < now }) "You have overdue reminders. Review them in Plan; CHRONOS will not reschedule them for you." else "Protect a recurring focus block around your quietest part of the day. This is guidance only, based on recorded and cached data.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
        }
        item {
            Text(
                "Recorded focus time does not include calendar commitments or background app usage.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val palette = LocalChronosPalette.current
    GlassCard(modifier = modifier, style = GlassStyle.SMOKED, cornerRadius = 20.dp, padding = 14.dp) {
        Icon(icon, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = palette.textPrimary)
        Text(title, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = palette.textTertiary)
    }
}

@Composable
private fun WeeklyFocusChart(days: List<DailyFocus>) {
    val palette = LocalChronosPalette.current
    val max = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(
                    text = if (day.minutes == 0) "—" else day.minutes.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary
                )
                Spacer(Modifier.height(5.dp))
                Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val barHeight = (size.height * day.minutes / max.toFloat()).coerceAtLeast(if (day.minutes > 0) 5.dp.toPx() else 2.dp.toPx())
                    drawRoundRect(
                        color = if (day.minutes > 0) palette.accentPrimary else palette.borderSubtle,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.56f, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(day.label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
    }
}

private fun buildDailyFocus(sessions: List<FocusSession>, weekStart: Long): List<DailyFocus> {
    val calendar = Calendar.getInstance().apply { timeInMillis = weekStart }
    return (0 until 7).map { offset ->
        val day = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        val start = day.timeInMillis
        val end = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val minutes = sessions.filter { it.startedAt in start until end }.sumOf { it.totalFocusMinutes }
        DailyFocus(start, SimpleDateFormat("EE", Locale.getDefault()).format(Date(start)), minutes)
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
