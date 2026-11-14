package com.chronos.app.ui.screens.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.calendar.CalendarViewModel
import com.chronos.app.reminders.ReminderViewModel
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import com.chronos.app.ui.components.SectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeOverview(showAgenda: Boolean, showReminders: Boolean, is24Hour: Boolean, onOpenPlan: () -> Unit) {
    if (!showAgenda && !showReminders) return
    val palette = LocalChronosPalette.current
    val calendar: CalendarViewModel = hiltViewModel()
    val reminders: ReminderViewModel = hiltViewModel()
    val events by calendar.events.collectAsStateWithLifecycle()
    val items by reminders.reminders.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val upcoming = events.filter { it.endTime >= now }.take(3)
    val due = items.filter { !it.isCompleted }.take(3)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpenPlan)) {
        SectionLabel("Today at a glance")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), style = GlassStyle.SMOKED, cornerRadius = 22.dp, padding = 18.dp) {
            if (showAgenda) {
                Text("UPCOMING", style = MaterialTheme.typography.labelSmall, color = palette.accentPrimary)
                if (upcoming.isEmpty()) Text("No cached events. Open Plan to connect or refresh your calendar.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                upcoming.forEach { event ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(if (event.allDay) "All day" else SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(event.startTime)),
                            style = MaterialTheme.typography.labelMedium, color = palette.accentPrimary, modifier = Modifier.padding(end = 12.dp))
                        Text(event.title, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, maxLines = 1)
                    }
                }
            }
            if (showAgenda && showReminders) Spacer(Modifier.height(14.dp))
            if (showReminders) {
                Text("REMINDERS", style = MaterialTheme.typography.labelSmall, color = palette.accentPrimary)
                if (due.isEmpty()) Text("Nothing waiting. Add a reminder in Plan.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                due.forEach { reminder ->
                    Text("${reminder.title} · ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(reminder.scheduledTime))}",
                        style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, modifier = Modifier.padding(vertical = 4.dp), maxLines = 1)
                }
            }
        }
    }
}
