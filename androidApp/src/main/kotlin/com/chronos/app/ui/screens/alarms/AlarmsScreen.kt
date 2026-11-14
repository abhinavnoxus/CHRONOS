package com.chronos.app.ui.screens.alarms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.app.data.model.Alarm
import com.chronos.app.theme.ChronosColors
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassDivider
import com.chronos.app.ui.components.GlassStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen() {
    // Sample alarms for preview
    val alarms = remember {
        listOf(
            Alarm(id = 1, hour = 6, minute = 30, label = "Morning Workout", repeatDays = Alarm.WEEKDAYS, isEnabled = true),
            Alarm(id = 2, hour = 7, minute = 0, label = "Get Ready", repeatDays = Alarm.EVERY_DAY, isEnabled = true),
            Alarm(id = 3, hour = 8, minute = 45, label = "Leave for Office", repeatDays = Alarm.WEEKDAYS, isEnabled = false),
            Alarm(id = 4, hour = 22, minute = 0, label = "Wind Down", repeatDays = Alarm.EVERY_DAY, isEnabled = true),
            Alarm(id = 5, hour = 23, minute = 0, label = "Sleep", repeatDays = Alarm.EVERY_DAY, isEnabled = true)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header
            Text(
                text = "Alarms",
                style = MaterialTheme.typography.displaySmall,
                color = ChronosColors.TextPrimary,
                fontWeight = FontWeight.Light
            )
            Text(
                text = "${alarms.count { it.isEnabled }} ACTIVE",
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 3.sp),
                color = ChronosColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(alarm = alarm)
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        // Floating add button
        FloatingActionButton(
            onClick = { /* TODO: Open alarm creation */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(64.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = ChronosColors.ChampagneGold,
            contentColor = ChronosColors.CharcoalDark
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add alarm",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun AlarmCard(alarm: Alarm) {
    var isEnabled by remember { mutableStateOf(alarm.isEnabled) }

    val cardAlpha by animateColorAsState(
        targetValue = if (isEnabled)
            ChronosColors.GlassSurface.copy(alpha = 0.06f)
        else
            ChronosColors.GlassSurface.copy(alpha = 0.02f),
        animationSpec = tween(300),
        label = "card_alpha"
    )

    GlassCard(
        style = if (isEnabled) GlassStyle.LUXURY else GlassStyle.SMOKED,
        modifier = Modifier.fillMaxWidth(),
        padding = 20.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Time
                Text(
                    text = alarm.formatTime(is24Hour = true),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp
                    ),
                    color = if (isEnabled) ChronosColors.TextPrimary
                    else ChronosColors.TextTertiary
                )

                // Label
                if (alarm.label.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isEnabled) ChronosColors.ChampagneGold
                        else ChronosColors.TextTertiary
                    )
                }

                // Repeat info
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getRepeatText(alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = ChronosColors.TextSecondary
                )

                // Day indicators for repeating alarms
                if (alarm.isRepeating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DayIndicators(alarm)
                }
            }

            // Toggle switch
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ChronosColors.ChampagneGold,
                    checkedTrackColor = ChronosColors.ChampagneGold.copy(alpha = 0.3f),
                    uncheckedThumbColor = ChronosColors.TextTertiary,
                    uncheckedTrackColor = ChronosColors.TextTertiary.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
fun DayIndicators(alarm: Alarm) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val dayValues = listOf(
        Alarm.DAY_MONDAY, Alarm.DAY_TUESDAY, Alarm.DAY_WEDNESDAY,
        Alarm.DAY_THURSDAY, Alarm.DAY_FRIDAY, Alarm.DAY_SATURDAY, Alarm.DAY_SUNDAY
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { index, day ->
            val active = (alarm.repeatDays and dayValues[index]) != 0
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) ChronosColors.ChampagneGold
                else ChronosColors.TextTertiary.copy(alpha = 0.4f),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun getRepeatText(alarm: Alarm): String {
    return when {
        !alarm.isRepeating -> "Once"
        alarm.repeatDays == Alarm.EVERY_DAY -> "Every day"
        alarm.repeatDays == Alarm.WEEKDAYS -> "Weekdays"
        alarm.repeatDays == Alarm.WEEKENDS -> "Weekends"
        else -> "Custom"
    }
}
