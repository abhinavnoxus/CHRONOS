package com.chronos.app.ui.screens.focus

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronos.app.settings.FocusRuntime
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen() {
    val palette = LocalChronosPalette.current
    val viewModel: FocusViewModel = hiltViewModel()
    val haptic = com.chronos.app.ui.components.rememberChronosHaptic(viewModel.preferences.collectAsStateWithLifecycle().value)
    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedMinutes by remember(preferences.defaultFocusMinutes) { mutableIntStateOf(preferences.defaultFocusMinutes) }
    var label by remember { mutableStateOf("Deep work") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            tick++
            viewModel.onTimerTick()
            delay(1_000L)
        }
    }
    val remaining = remember(runtime, tick) { viewModel.remainingMillis(runtime) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Focus studio", style = MaterialTheme.typography.displaySmall, color = palette.textPrimary)
                Text("One interval at a time.", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
        if (!runtime.isActive) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.LUXURY, cornerRadius = 24.dp, padding = 20.dp) {
                    Text("Choose your session", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(50) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Session label") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("FOCUS LENGTH", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = palette.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 45, 60, 90).forEach { duration ->
                            FilterChip(
                                selected = selectedMinutes == duration,
                                onClick = { selectedMinutes = duration },
                                label = { Text("$duration min") }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { haptic(); viewModel.start(label, selectedMinutes) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Start focus")
                    }
                }
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.SMOKED, cornerRadius = 22.dp) {
                    Text("A gentle rhythm", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "When a focus interval ends, CHRONOS starts a short break. Every fourth completed interval uses a longer break. The timer is stored on this device and resumes from its saved deadline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        } else {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), style = GlassStyle.LUXURY, cornerRadius = 26.dp, padding = 22.dp) {
                    Text(
                        text = when (runtime.phase) {
                            FocusViewModel.PHASE_BREAK -> "SHORT BREAK"
                            FocusViewModel.PHASE_LONG_BREAK -> "LONG BREAK"
                            else -> "FOCUS"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = palette.accentPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    FocusProgressRing(runtime, remaining)
                    Spacer(Modifier.height(14.dp))
                    Text(runtime.label, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${runtime.completedFocusSessions} completed focus interval${if (runtime.completedFocusSessions == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { if (runtime.isPaused) viewModel.resume() else viewModel.pause() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(if (runtime.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (runtime.isPaused) "Resume" else "Pause")
                        }
                        OutlinedButton(
                            onClick = viewModel::stop,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Finish")
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { com.chronos.app.ui.components.SectionLabel("Session history") }
            items(history.take(10), key = { it.id }) { session ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(session.label, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        Text(java.text.SimpleDateFormat("MMM d · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.startedAt)), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                    Text("${session.totalFocusMinutes} min · ${session.completedSessions} rounds", style = MaterialTheme.typography.labelMedium, color = palette.accentPrimary)
                }
            }
        }
        item {
            Text(
                "Focus history records completed and stopped sessions; it does not block notifications or other apps.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FocusProgressRing(runtime: FocusRuntime, remainingMillis: Long) {
    val palette = LocalChronosPalette.current
    val durationMillis = when (runtime.phase) {
        FocusViewModel.PHASE_BREAK -> runtime.breakMinutes * 60_000L
        FocusViewModel.PHASE_LONG_BREAK -> runtime.longBreakMinutes * 60_000L
        else -> runtime.focusMinutes * 60_000L
    }.coerceAtLeast(1L)
    val progress = (1f - (remainingMillis.toFloat() / durationMillis).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val totalSeconds = (remainingMillis + 999L) / 1_000L
    val time = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(236.dp).semantics { contentDescription = "$time remaining" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            drawArc(
                color = palette.borderSubtle,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = palette.accentPrimary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            time,
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                fontSize = 42.sp
            ),
            color = palette.textPrimary
        )
    }
}
