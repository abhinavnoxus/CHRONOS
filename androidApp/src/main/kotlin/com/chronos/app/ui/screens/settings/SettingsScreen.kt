package com.chronos.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.app.theme.ChronosColors
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassDivider
import com.chronos.app.ui.components.GlassStyle

data class SettingsCategory(
    val title: String,
    val icon: ImageVector,
    val items: List<SettingsItem>
)

data class SettingsItem(
    val title: String,
    val subtitle: String = "",
    val type: SettingsItemType = SettingsItemType.NAVIGATION,
    val booleanValue: Boolean = false
)

enum class SettingsItemType {
    NAVIGATION, TOGGLE, SELECTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val categories = remember {
        listOf(
            SettingsCategory(
                "Appearance",
                Icons.Default.Palette,
                listOf(
                    SettingsItem("Theme", "Dark mode", SettingsItemType.SELECTION),
                    SettingsItem("Glass Style", "Luxury Glass", SettingsItemType.SELECTION),
                    SettingsItem("Accent Colour", "Champagne Gold", SettingsItemType.SELECTION),
                    SettingsItem("Animation Intensity", "Full", SettingsItemType.SELECTION),
                    SettingsItem("Wallpaper Colours", "Use adaptive colours", SettingsItemType.TOGGLE, true)
                )
            ),
            SettingsCategory(
                "Clock",
                Icons.Default.Schedule,
                listOf(
                    SettingsItem("24-Hour Format", "", SettingsItemType.TOGGLE, true),
                    SettingsItem("Show Seconds", "", SettingsItemType.TOGGLE, true),
                    SettingsItem("Show Date", "", SettingsItemType.TOGGLE, true),
                    SettingsItem("Clock Style", "Digital + Analog", SettingsItemType.SELECTION)
                )
            ),
            SettingsCategory(
                "Alarms",
                Icons.Default.Alarm,
                listOf(
                    SettingsItem("Default Snooze", "5 minutes", SettingsItemType.SELECTION),
                    SettingsItem("Gradual Volume", "Increase over 30 seconds", SettingsItemType.TOGGLE, true),
                    SettingsItem("Default Vibration", "", SettingsItemType.TOGGLE, true),
                    SettingsItem("Default Ringtone", "System default", SettingsItemType.SELECTION)
                )
            ),
            SettingsCategory(
                "Calendar",
                Icons.Default.CalendarMonth,
                listOf(
                    SettingsItem("Connected Accounts", "1 Google account", SettingsItemType.NAVIGATION),
                    SettingsItem("Smart Suggestions", "Suggest alarms before events", SettingsItemType.TOGGLE, true),
                    SettingsItem("Conflict Detection", "Warn about overlaps", SettingsItemType.TOGGLE, true)
                )
            ),
            SettingsCategory(
                "Sound & Vibration",
                Icons.Default.VolumeUp,
                listOf(
                    SettingsItem("Notification Sound", "Default", SettingsItemType.SELECTION),
                    SettingsItem("Haptic Feedback", "", SettingsItemType.TOGGLE, true)
                )
            ),
            SettingsCategory(
                "Advanced",
                Icons.Default.Tune,
                listOf(
                    SettingsItem("Battery Optimization", "Allow background activity", SettingsItemType.NAVIGATION),
                    SettingsItem("Exact Alarms", "Permission granted", SettingsItemType.NAVIGATION),
                    SettingsItem("Backup & Restore", "", SettingsItemType.NAVIGATION),
                    SettingsItem("Diagnostics", "View logs", SettingsItemType.NAVIGATION)
                )
            ),
            SettingsCategory(
                "About",
                Icons.Default.Info,
                listOf(
                    SettingsItem("CHRONOS", "Version 2.0.0", SettingsItemType.NAVIGATION),
                    SettingsItem("Privacy Policy", "", SettingsItemType.NAVIGATION),
                    SettingsItem("Open Source Licences", "", SettingsItemType.NAVIGATION)
                )
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = ChronosColors.TextPrimary,
            fontWeight = FontWeight.Light
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories.size) { catIndex ->
                val category = categories[catIndex]

                GlassCard(
                    style = GlassStyle.LUXURY,
                    modifier = Modifier.fillMaxWidth(),
                    padding = 0.dp
                ) {
                    // Category header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = ChronosColors.ChampagneGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ChronosColors.ChampagneGold
                        )
                    }

                    GlassDivider(modifier = Modifier.padding(horizontal = 20.dp))

                    // Items
                    category.items.forEachIndexed { index, item ->
                        SettingsRow(item)
                        if (index < category.items.size - 1) {
                            GlassDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = ChronosColors.GlassBorder.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun SettingsRow(item: SettingsItem) {
    var toggled by remember { mutableStateOf(item.booleanValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = ChronosColors.TextPrimary
            )
            if (item.subtitle.isNotEmpty()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChronosColors.TextSecondary
                )
            }
        }

        when (item.type) {
            SettingsItemType.NAVIGATION -> {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ChronosColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            SettingsItemType.TOGGLE -> {
                Switch(
                    checked = toggled,
                    onCheckedChange = { toggled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ChronosColors.ChampagneGold,
                        checkedTrackColor = ChronosColors.ChampagneGold.copy(alpha = 0.3f),
                        uncheckedThumbColor = ChronosColors.TextTertiary,
                        uncheckedTrackColor = ChronosColors.TextTertiary.copy(alpha = 0.2f)
                    )
                )
            }
            SettingsItemType.SELECTION -> {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ChronosColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
