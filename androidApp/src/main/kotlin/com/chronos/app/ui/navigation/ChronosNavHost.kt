package com.chronos.app.ui.navigation

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.vector.ImageVector
import com.chronos.app.settings.SettingsViewModel
import com.chronos.app.theme.LocalChronosPalette
import com.chronos.app.theme.chronosMotionDuration
import com.chronos.app.ui.screens.alarms.AlarmsScreen
import com.chronos.app.ui.screens.clock.ClockScreen
import com.chronos.app.ui.screens.focus.FocusScreen
import com.chronos.app.ui.screens.insights.InsightsScreen
import com.chronos.app.ui.screens.schedule.ScheduleScreen
import com.chronos.app.ui.screens.settings.SettingsScreen

private data class Destination(val route: String, val title: String, val icon: ImageVector)

private object Routes {
    const val HOME = "home"
    const val PLAN = "plan"
    const val ALARMS = "alarms"
    const val FOCUS = "focus"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
}

private val bottomItems = listOf(
    Destination(Routes.HOME, "Home", Icons.Default.Home),
    Destination(Routes.PLAN, "Plan", Icons.Default.CalendarMonth),
    Destination(Routes.ALARMS, "Alarms", Icons.Default.Alarm),
    Destination(Routes.FOCUS, "Focus", Icons.Default.Timelapse),
    Destination(Routes.INSIGHTS, "Insights", Icons.Default.AutoGraph)
)

@Composable
fun ChronosNavHost(navController: NavHostController = rememberNavController()) {
    val palette = LocalChronosPalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val preferences by settingsViewModel.preferences.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val context = LocalContext.current
    val systemMotionEnabled = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
            .getOrDefault(true)
    }
    val motionDuration = if (systemMotionEnabled) chronosMotionDuration(preferences, standardMillis = 240) else 0

    Scaffold(
        containerColor = palette.backgroundPrimary,
        bottomBar = {
            if (currentDestination?.route != Routes.SETTINGS) {
                NavigationBar(containerColor = palette.backgroundSecondary) {
                    bottomItems.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.accentPrimary,
                                selectedTextColor = palette.accentPrimary,
                                unselectedIconColor = palette.textTertiary,
                                unselectedTextColor = palette.textTertiary,
                                indicatorColor = palette.accentPrimary.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                if (motionDuration == 0) EnterTransition.None else fadeIn(tween(motionDuration))
            },
            exitTransition = {
                if (motionDuration == 0) ExitTransition.None else fadeOut(tween(motionDuration))
            },
            popEnterTransition = {
                if (motionDuration == 0) EnterTransition.None else fadeIn(tween(motionDuration))
            },
            popExitTransition = {
                if (motionDuration == 0) ExitTransition.None else fadeOut(tween(motionDuration))
            }
        ) {
            composable(
                Routes.HOME,
                deepLinks = listOf(navDeepLink { uriPattern = "chronos://app/home" })
            ) {
                ClockScreen(
                    onNavigate = { route -> navigateOnce(navController, route) },
                    onOpenSettings = { navigateOnce(navController, Routes.SETTINGS) }
                )
            }
            composable(Routes.PLAN) { ScheduleScreen() }
            composable(Routes.ALARMS) { AlarmsScreen() }
            composable(Routes.FOCUS) { FocusScreen() }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun navigateOnce(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) { launchSingleTop = true }
}
