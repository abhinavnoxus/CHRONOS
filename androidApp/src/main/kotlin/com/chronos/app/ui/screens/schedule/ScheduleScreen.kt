package com.chronos.app.ui.screens.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.app.theme.ChronosColors
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassDivider
import com.chronos.app.ui.components.GlassStyle
import java.text.SimpleDateFormat
import java.util.*

data class TimelineItem(
    val time: String,
    val title: String,
    val subtitle: String = "",
    val type: ItemType = ItemType.EVENT,
    val color: Color = ChronosColors.ChampagneGold
) {
    enum class ItemType { EVENT, REMINDER, GAP, SUGGESTION }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val today = remember { dateFormat.format(Date()) }

    // Sample timeline data
    val timelineItems = remember {
        listOf(
            TimelineItem("07:00", "Morning Routine", "Reminder", TimelineItem.ItemType.REMINDER, ChronosColors.MutedBlue),
            TimelineItem("09:00", "Deep Work Session", "Focus · 2 hours", TimelineItem.ItemType.EVENT, ChronosColors.ChampagneGold),
            TimelineItem("11:00", "Free Block", "1h 30m available", TimelineItem.ItemType.GAP, ChronosColors.Success),
            TimelineItem("12:30", "Lunch", "Calendar event", TimelineItem.ItemType.EVENT, ChronosColors.MutedBlue),
            TimelineItem("14:00", "Team Meeting", "Google Meet · 1 hour", TimelineItem.ItemType.EVENT, ChronosColors.ChampagneGold),
            TimelineItem("15:30", "Suggested: Prep for tomorrow", "Based on your pattern", TimelineItem.ItemType.SUGGESTION, ChronosColors.Warning),
            TimelineItem("16:00", "Code Review", "Calendar event", TimelineItem.ItemType.EVENT, ChronosColors.MutedBlue),
            TimelineItem("18:00", "Evening Revision", "Reminder", TimelineItem.ItemType.REMINDER, ChronosColors.ChampagneGold)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Text(
            text = "Schedule",
            style = MaterialTheme.typography.displaySmall,
            color = ChronosColors.TextPrimary,
            fontWeight = FontWeight.Light
        )
        Text(
            text = today.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 3.sp),
            color = ChronosColors.TextTertiary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Day summary
        GlassCard(
            style = GlassStyle.LUXURY,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem("Events", "5")
                SummaryItem("Reminders", "2")
                SummaryItem("Free", "3h")
                SummaryItem("Conflicts", "0", ChronosColors.Success)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timeline
        Text(
            text = "TODAY'S TIMELINE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
            color = ChronosColors.TextTertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(timelineItems) { item ->
                TimelineEntry(item)
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, color: Color = ChronosColors.TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Light
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ChronosColors.TextTertiary
        )
    }
}

@Composable
fun TimelineEntry(item: TimelineItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time column
        Text(
            text = item.time,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            color = ChronosColors.TextSecondary,
            modifier = Modifier.width(56.dp)
        )

        // Timeline indicator
        Column(
            modifier = Modifier
                .width(24.dp)
                .padding(start = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .then(
                        Modifier.padding(1.dp)
                    )
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = item.color.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
        }

        // Content
        GlassCard(
            style = when (item.type) {
                TimelineItem.ItemType.SUGGESTION -> GlassStyle.FROSTED
                TimelineItem.ItemType.GAP -> GlassStyle.SMOKED
                else -> GlassStyle.LUXURY
            },
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            padding = 14.dp
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = ChronosColors.TextPrimary
            )
            if (item.subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChronosColors.TextSecondary
                )
            }

            if (item.type == TimelineItem.ItemType.SUGGESTION) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ChronosColors.ChampagneGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Suggestion",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChronosColors.ChampagneGold
                    )
                }
            }
        }
    }
}
