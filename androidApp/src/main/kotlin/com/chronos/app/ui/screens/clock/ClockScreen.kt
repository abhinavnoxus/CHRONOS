package com.chronos.app.ui.screens.clock

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronos.app.alarm.AlarmViewModel
import com.chronos.app.alarm.engine.AlarmTimeCalculator
import com.chronos.app.data.model.Alarm
import com.chronos.app.settings.ChronosPreferences
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClockScreen(
    onNavigate: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalChronosPalette.current
    val clockViewModel: ClockViewModel = hiltViewModel()
    val preferences by clockViewModel.preferences.collectAsState()
    val alarmViewModel: AlarmViewModel = hiltViewModel()
    val enabledAlarms by alarmViewModel.enabledAlarms.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CHRONOS",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                        color = palette.accentPrimary
                    )
                    Text(
                        text = "Your time, in focus.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Open settings", tint = palette.textSecondary)
                }
            }
        }
        item { ClockHero(preferences) }
        item { NextAlarmCard(alarms = enabledAlarms, is24Hour = preferences.is24Hour, onOpenAlarms = { onNavigate("alarms") }) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "QUICK ACCESS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = palette.textTertiary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        title = "Alarms",
                        subtitle = "Create or adjust",
                        icon = Icons.Default.Alarm,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("alarms") }
                    )
                    ActionTile(
                        title = "Plan",
                        subtitle = "Today's schedule",
                        icon = Icons.Default.CalendarMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("plan") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        title = "Focus",
                        subtitle = "Start a session",
                        icon = Icons.Default.Timelapse,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("focus") }
                    )
                    ActionTile(
                        title = "Insights",
                        subtitle = "Recorded time",
                        icon = Icons.Default.AutoGraph,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("insights") }
                    )
                }
            }
        }
        item {
            Text(
                text = "Offline by design · Your schedules stay on this device",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ClockHero(preferences: ChronosPreferences) {
    val palette = LocalChronosPalette.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(preferences.showSeconds) {
        while (true) {
            now = System.currentTimeMillis()
            val step = if (preferences.showSeconds) 1_000L else 60_000L
            delay((step - (now % step)).coerceAtLeast(50L))
        }
    }
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val useAnalog = preferences.clockStyle.equals("analog", true) || preferences.clockStyle.equals("hybrid", true)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        style = GlassStyle.LUXURY,
        cornerRadius = 26.dp,
        padding = 22.dp
    ) {
        if (preferences.showDate) {
            Text(
                text = SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()).format(Date(now)).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp),
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        if (useAnalog) {
            Spacer(Modifier.height(18.dp))
            AnalogClock(
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE),
                second = calendar.get(Calendar.SECOND),
                modifier = Modifier.size(168.dp).align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.height(if (useAnalog) 18.dp else 8.dp))
        Text(
            text = formatClockTime(now, preferences),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                fontSize = 54.sp,
                letterSpacing = (-1.8).sp
            ),
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (preferences.is24Hour) "LOCAL TIME · 24-HOUR" else "LOCAL TIME · 12-HOUR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = palette.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
private fun NextAlarmCard(alarms: List<Alarm>, is24Hour: Boolean, onOpenAlarms: () -> Unit) {
    val palette = LocalChronosPalette.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val next = remember(alarms, now / 30_000L) {
        alarms.mapNotNull { alarm ->
            AlarmTimeCalculator.calculateNextTriggerTime(alarm, now)?.let { alarm to it }
        }.minByOrNull { it.second }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpenAlarms),
        style = GlassStyle.SMOKED,
        cornerRadius = 22.dp,
        padding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NEXT ALARM",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = palette.textTertiary
                )
                Spacer(Modifier.height(6.dp))
                if (next == null) {
                    Text("No alarms scheduled", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text("Tap to create one", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                } else {
                    val alarm = next.first
                    val triggerAt = next.second
                    Text(
                        text = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(triggerAt)),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace),
                        color = palette.accentPrimary
                    )
                    val timeLeft = ((triggerAt - now).coerceAtLeast(0L) / 60_000L)
                    val detail = SimpleDateFormat("EEE · MMM d", Locale.getDefault()).format(Date(triggerAt))
                    Text(
                        text = "${alarm.label.ifBlank { "Alarm" }} · $detail · in ${timeLeft / 60}h ${timeLeft % 60}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
            Icon(Icons.Default.Alarm, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalChronosPalette.current
    GlassCard(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        style = GlassStyle.LUXURY,
        cornerRadius = 18.dp,
        padding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AnalogClock(hour: Int, minute: Int, second: Int, modifier: Modifier = Modifier) {
    val palette = LocalChronosPalette.current
    Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f
        drawCircle(palette.borderSubtle, radius = radius, center = center, style = Stroke(width = 1.4.dp.toPx()))
        for (mark in 0 until 60) {
            val angle = Math.toRadians(mark * 6.0 - 90.0)
            val major = mark % 5 == 0
            val inner = radius * if (major) 0.82f else 0.9f
            val outer = radius * 0.97f
            drawLine(
                color = if (major) palette.accentPrimary else palette.textTertiary.copy(alpha = 0.58f),
                start = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = if (major) 1.8.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        fun hand(degrees: Double, length: Float, width: Float, color: Color) {
            val angle = Math.toRadians(degrees - 90.0)
            drawLine(
                color = color,
                start = center,
                end = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * length, center.y + sin(angle).toFloat() * length),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
        hand((hour % 12) * 30.0 + minute * 0.5, radius * 0.48f, 4.dp.toPx(), palette.accentPrimary)
        hand(minute * 6.0 + second * 0.1, radius * 0.72f, 2.8.dp.toPx(), palette.textPrimary)
        if (second >= 0) hand(second * 6.0, radius * 0.79f, 1.2.dp.toPx(), palette.accentSecondary)
        drawCircle(palette.accentPrimary, radius = 4.dp.toPx(), center = center)
    }
}

private fun formatClockTime(now: Long, preferences: ChronosPreferences): String {
    val pattern = buildString {
        append(if (preferences.is24Hour) "HH:mm" else "h:mm")
        if (preferences.showSeconds) append(":ss")
        if (!preferences.is24Hour) append(" a")
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
}
