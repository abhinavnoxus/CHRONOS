package com.chronos.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chronosDataStore: DataStore<Preferences> by preferencesDataStore(name = "chronos_settings")

data class FocusRuntime(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val phase: String = "focus",
    val label: String = "Deep work",
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
    val completedFocusSessions: Int = 0,
    val totalFocusMillis: Long = 0L,
    val sessionStartedAt: Long = 0L,
    val deadlineAt: Long = 0L,
    val deadlineElapsedRealtime: Long = 0L,
    val pausedRemainingMillis: Long = 0L,
    val bootCount: Int = 0,
    val sessionId: Long = 0L
)

data class ChronosPreferences(
    val themeMode: String = "dark",
    val glassStyle: String = "luxury",
    val accentArgb: Long = 0xFFC6A36BL,
    val animationIntensity: String = "balanced",
    val performanceMode: String = "balanced",
    val is24Hour: Boolean = true,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val clockStyle: String = "hybrid",
    val hapticFeedback: Boolean = true,
    val showAgenda: Boolean = true,
    val showReminders: Boolean = true,
    val showQuickActions: Boolean = true,
    val defaultFocusMinutes: Int = 25,
    val defaultBreakMinutes: Int = 5,
    val defaultLongBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4
) {
    companion object {
        val Default = ChronosPreferences()
    }
}

@Singleton
class ChronosSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.chronosDataStore

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val glassStyle = stringPreferencesKey("glass_style")
        val accentArgb = longPreferencesKey("accent_argb")
        val legacyAccentColor = stringPreferencesKey("accent_color")
        val legacyCustomAccentColor = longPreferencesKey("custom_accent_color")
        val animationIntensity = stringPreferencesKey("animation_intensity")
        val performanceMode = stringPreferencesKey("performance_mode")
        val is24Hour = booleanPreferencesKey("is_24_hour")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val showDate = booleanPreferencesKey("show_date")
        val clockStyle = stringPreferencesKey("clock_style")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val showAgenda = booleanPreferencesKey("home_show_agenda")
        val showReminders = booleanPreferencesKey("home_show_reminders")
        val showQuickActions = booleanPreferencesKey("home_show_quick_actions")
        val defaultFocusMinutes = intPreferencesKey("default_focus_minutes")
        val defaultBreakMinutes = intPreferencesKey("default_break_minutes")
        val defaultLongBreakMinutes = intPreferencesKey("default_long_break_minutes")
        val sessionsBeforeLongBreak = intPreferencesKey("sessions_before_long_break")
        val useWallpaperColors = booleanPreferencesKey("use_wallpaper_colors")
        val dashboardOrder = stringPreferencesKey("dashboard_order")
        val focusActive = booleanPreferencesKey("focus_active")
        val focusPaused = booleanPreferencesKey("focus_paused")
        val focusPhase = stringPreferencesKey("focus_phase")
        val focusLabel = stringPreferencesKey("focus_label")
        val focusMinutes = androidx.datastore.preferences.core.intPreferencesKey("focus_minutes")
        val breakMinutes = androidx.datastore.preferences.core.intPreferencesKey("break_minutes")
        val longBreakMinutes = androidx.datastore.preferences.core.intPreferencesKey("long_break_minutes")
        val sessionsBeforeLongBreak = androidx.datastore.preferences.core.intPreferencesKey("sessions_before_long_break")
        val completedFocusSessions = androidx.datastore.preferences.core.intPreferencesKey("completed_focus_sessions")
        val totalFocusMillis = longPreferencesKey("total_focus_millis")
        val focusSessionStartedAt = longPreferencesKey("focus_session_started_at")
        val focusDeadlineAt = longPreferencesKey("focus_deadline_at")
        val focusDeadlineElapsed = longPreferencesKey("focus_deadline_elapsed")
        val focusPausedRemaining = longPreferencesKey("focus_paused_remaining")
        val focusBootCount = androidx.datastore.preferences.core.intPreferencesKey("focus_boot_count")
        val focusSessionId = longPreferencesKey("focus_session_id")
    }

    val focusRuntime: Flow<FocusRuntime> = dataStore.data.map { values ->
        FocusRuntime(
            isActive = values[Keys.focusActive] ?: false,
            isPaused = values[Keys.focusPaused] ?: false,
            phase = values[Keys.focusPhase] ?: "focus",
            label = values[Keys.focusLabel] ?: "Deep work",
            focusMinutes = values[Keys.focusMinutes] ?: 25,
            breakMinutes = values[Keys.breakMinutes] ?: 5,
            longBreakMinutes = values[Keys.longBreakMinutes] ?: 15,
            sessionsBeforeLongBreak = values[Keys.sessionsBeforeLongBreak] ?: 4,
            completedFocusSessions = values[Keys.completedFocusSessions] ?: 0,
            totalFocusMillis = values[Keys.totalFocusMillis] ?: 0L,
            sessionStartedAt = values[Keys.focusSessionStartedAt] ?: 0L,
            deadlineAt = values[Keys.focusDeadlineAt] ?: 0L,
            deadlineElapsedRealtime = values[Keys.focusDeadlineElapsed] ?: 0L,
            pausedRemainingMillis = values[Keys.focusPausedRemaining] ?: 0L,
            bootCount = values[Keys.focusBootCount] ?: 0,
            sessionId = values[Keys.focusSessionId] ?: 0L
        )
    }

    suspend fun saveFocusRuntime(runtime: FocusRuntime) {
        dataStore.edit { values ->
            values[Keys.focusActive] = runtime.isActive
            values[Keys.focusPaused] = runtime.isPaused
            values[Keys.focusPhase] = runtime.phase
            values[Keys.focusLabel] = runtime.label
            values[Keys.focusMinutes] = runtime.focusMinutes
            values[Keys.breakMinutes] = runtime.breakMinutes
            values[Keys.longBreakMinutes] = runtime.longBreakMinutes
            values[Keys.sessionsBeforeLongBreak] = runtime.sessionsBeforeLongBreak
            values[Keys.completedFocusSessions] = runtime.completedFocusSessions
            values[Keys.totalFocusMillis] = runtime.totalFocusMillis
            values[Keys.focusSessionStartedAt] = runtime.sessionStartedAt
            values[Keys.focusDeadlineAt] = runtime.deadlineAt
            values[Keys.focusDeadlineElapsed] = runtime.deadlineElapsedRealtime
            values[Keys.focusPausedRemaining] = runtime.pausedRemainingMillis
            values[Keys.focusBootCount] = runtime.bootCount
            values[Keys.focusSessionId] = runtime.sessionId
        }
    }

    suspend fun clearFocusRuntime() {
        dataStore.edit { values ->
            values.remove(Keys.focusActive)
            values.remove(Keys.focusPaused)
            values.remove(Keys.focusPhase)
            values.remove(Keys.focusLabel)
            values.remove(Keys.focusMinutes)
            values.remove(Keys.breakMinutes)
            values.remove(Keys.longBreakMinutes)
            values.remove(Keys.sessionsBeforeLongBreak)
            values.remove(Keys.completedFocusSessions)
            values.remove(Keys.totalFocusMillis)
            values.remove(Keys.focusSessionStartedAt)
            values.remove(Keys.focusDeadlineAt)
            values.remove(Keys.focusDeadlineElapsed)
            values.remove(Keys.focusPausedRemaining)
            values.remove(Keys.focusBootCount)
            values.remove(Keys.focusSessionId)
        }
    }

    val preferences: Flow<ChronosPreferences> = dataStore.data.map { values ->
        val legacyAccent = when (values[Keys.legacyAccentColor]?.lowercase()) {
            "gold" -> 0xFFD4A853L
            "blue" -> 0xFF5B7FA5L
            "custom" -> values[Keys.legacyCustomAccentColor]
            else -> values[Keys.legacyCustomAccentColor]
        }
        val animation = values[Keys.animationIntensity]?.let { stored ->
            when (stored.lowercase()) {
                "full" -> "cinematic"
                "none" -> "reduced"
                else -> stored
            }
        }
        val storedClockStyle = values[Keys.clockStyle]?.let { stored ->
            if (stored.equals("both", ignoreCase = true)) "hybrid" else stored
        }

        ChronosPreferences(
            themeMode = values[Keys.themeMode] ?: ChronosPreferences.Default.themeMode,
            glassStyle = values[Keys.glassStyle] ?: ChronosPreferences.Default.glassStyle,
            accentArgb = values[Keys.accentArgb] ?: legacyAccent ?: ChronosPreferences.Default.accentArgb,
            animationIntensity = animation ?: ChronosPreferences.Default.animationIntensity,
            performanceMode = values[Keys.performanceMode] ?: ChronosPreferences.Default.performanceMode,
            is24Hour = values[Keys.is24Hour] ?: ChronosPreferences.Default.is24Hour,
            showSeconds = values[Keys.showSeconds] ?: ChronosPreferences.Default.showSeconds,
            showDate = values[Keys.showDate] ?: ChronosPreferences.Default.showDate,
            clockStyle = storedClockStyle ?: ChronosPreferences.Default.clockStyle,
            hapticFeedback = values[Keys.hapticFeedback] ?: ChronosPreferences.Default.hapticFeedback,
            showAgenda = values[Keys.showAgenda] ?: true,
            showReminders = values[Keys.showReminders] ?: true,
            showQuickActions = values[Keys.showQuickActions] ?: true,
            defaultFocusMinutes = (values[Keys.defaultFocusMinutes] ?: 25).coerceIn(5, 180),
            defaultBreakMinutes = (values[Keys.defaultBreakMinutes] ?: 5).coerceIn(1, 30),
            defaultLongBreakMinutes = (values[Keys.defaultLongBreakMinutes] ?: 15).coerceIn(5, 60),
            sessionsBeforeLongBreak = (values[Keys.sessionsBeforeLongBreak] ?: 4).coerceIn(2, 8)
        )
    }

    suspend fun setThemeMode(value: String) = dataStore.edit { it[Keys.themeMode] = value }
    suspend fun setGlassStyle(value: String) = dataStore.edit { it[Keys.glassStyle] = value }
    suspend fun setAccentArgb(value: Long) = dataStore.edit { it[Keys.accentArgb] = value }
    suspend fun setAnimationIntensity(value: String) = dataStore.edit { it[Keys.animationIntensity] = value }
    suspend fun setPerformanceMode(value: String) = dataStore.edit { it[Keys.performanceMode] = value }
    suspend fun set24Hour(value: Boolean) = dataStore.edit { it[Keys.is24Hour] = value }
    suspend fun setShowSeconds(value: Boolean) = dataStore.edit { it[Keys.showSeconds] = value }
    suspend fun setShowDate(value: Boolean) = dataStore.edit { it[Keys.showDate] = value }
    suspend fun setClockStyle(value: String) = dataStore.edit { it[Keys.clockStyle] = value }
    suspend fun setHapticFeedback(value: Boolean) = dataStore.edit { it[Keys.hapticFeedback] = value }
    suspend fun setShowAgenda(value: Boolean) = dataStore.edit { it[Keys.showAgenda] = value }
    suspend fun setShowReminders(value: Boolean) = dataStore.edit { it[Keys.showReminders] = value }
    suspend fun setShowQuickActions(value: Boolean) = dataStore.edit { it[Keys.showQuickActions] = value }
    suspend fun setFocusDefaults(focus: Int, shortBreak: Int, longBreak: Int, rounds: Int) = dataStore.edit {
        it[Keys.defaultFocusMinutes] = focus.coerceIn(5, 180)
        it[Keys.defaultBreakMinutes] = shortBreak.coerceIn(1, 30)
        it[Keys.defaultLongBreakMinutes] = longBreak.coerceIn(5, 60)
        it[Keys.sessionsBeforeLongBreak] = rounds.coerceIn(2, 8)
    }

    // Kept for forward-compatible dashboard customization; no current card is hidden by default.
    val dashboardOrder: Flow<String> = dataStore.data.map { it[Keys.dashboardOrder] ?: "clock,next_alarm,quick_actions" }
    suspend fun setDashboardOrder(value: String) = dataStore.edit { it[Keys.dashboardOrder] = value }
}
