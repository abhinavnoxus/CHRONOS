package com.chronos.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chronos.app.theme.ChronosColors
import com.chronos.app.ui.screens.alarms.AlarmsScreen
import com.chronos.app.ui.screens.clock.ClockScreen
import com.chronos.app.ui.screens.schedule.ScheduleScreen
import com.chronos.app.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Clock : Screen("clock", "Clock", Icons.Default.Schedule)
    data object Schedule : Screen("schedule", "Schedule", Icons.Default.CalendarMonth)
    data object Alarms : Screen("alarms", "Alarms", Icons.Default.Alarm)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Clock,
    Screen.Schedule,
    Screen.Alarms,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronosNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = ChronosColors.CharcoalMedium,
                contentColor = ChronosColors.TextPrimary
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ChronosColors.ChampagneGold,
                            selectedTextColor = ChronosColors.ChampagneGold,
                            unselectedIconColor = ChronosColors.TextTertiary,
                            unselectedTextColor = ChronosColors.TextTertiary,
                            indicatorColor = ChronosColors.ChampagneGold.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Clock.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Screen.Clock.route) { ClockScreen() }
            composable(Screen.Schedule.route) { ScheduleScreen() }
            composable(Screen.Alarms.route) { AlarmsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
