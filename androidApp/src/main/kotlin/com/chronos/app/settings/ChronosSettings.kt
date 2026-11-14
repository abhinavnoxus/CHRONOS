package com.chronos.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chronos_settings")

@Singleton
class ChronosSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // Appearance
    object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "dark", "light", "adaptive"
        val GLASS_STYLE = stringPreferencesKey("glass_style") // "luxury", "smoked", "frosted"
        val ACCENT_COLOR = stringPreferencesKey("accent_color") // "gold", "blue", "custom"
        val CUSTOM_ACCENT_COLOR = longPreferencesKey("custom_accent_color")
        val ANIMATION_INTENSITY = stringPreferencesKey("animation_intensity") // "full", "reduced", "none"
        val USE_WALLPAPER_COLORS = booleanPreferencesKey("use_wallpaper_colors")

        // Clock
        val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
        val SHOW_SECONDS = booleanPreferencesKey("show_seconds")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val CLOCK_STYLE = stringPreferencesKey("clock_style") // "digital", "analog", "both"

        // Alarm
        val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val DEFAULT_RINGTONE = stringPreferencesKey("default_ringtone")
        val DEFAULT_VIBRATE = booleanPreferencesKey("default_vibrate")
        val GRADUAL_VOLUME = booleanPreferencesKey("gradual_volume")
        val DEFAULT_VOLUME = floatPreferencesKey("default_volume")

        // Sound
        val NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")

        // Dashboard
        val DASHBOARD_ORDER = stringPreferencesKey("dashboard_order")
    }

    // Theme
    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "dark" }
    suspend fun setThemeMode(mode: String) { dataStore.edit { it[Keys.THEME_MODE] = mode } }

    val glassStyle: Flow<String> = dataStore.data.map { it[Keys.GLASS_STYLE] ?: "luxury" }
    suspend fun setGlassStyle(style: String) { dataStore.edit { it[Keys.GLASS_STYLE] = style } }

    val accentColor: Flow<String> = dataStore.data.map { it[Keys.ACCENT_COLOR] ?: "gold" }
    suspend fun setAccentColor(color: String) { dataStore.edit { it[Keys.ACCENT_COLOR] = color } }

    val animationIntensity: Flow<String> = dataStore.data.map { it[Keys.ANIMATION_INTENSITY] ?: "full" }
    suspend fun setAnimationIntensity(intensity: String) { dataStore.edit { it[Keys.ANIMATION_INTENSITY] = intensity } }

    // Clock
    val is24Hour: Flow<Boolean> = dataStore.data.map { it[Keys.IS_24_HOUR] ?: true }
    suspend fun set24Hour(enabled: Boolean) { dataStore.edit { it[Keys.IS_24_HOUR] = enabled } }

    val showSeconds: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_SECONDS] ?: true }
    suspend fun setShowSeconds(show: Boolean) { dataStore.edit { it[Keys.SHOW_SECONDS] = show } }

    // Alarm defaults
    val defaultSnoozeMinutes: Flow<Int> = dataStore.data.map { it[Keys.DEFAULT_SNOOZE_MINUTES] ?: 5 }
    suspend fun setDefaultSnoozeMinutes(minutes: Int) { dataStore.edit { it[Keys.DEFAULT_SNOOZE_MINUTES] = minutes } }

    val gradualVolume: Flow<Boolean> = dataStore.data.map { it[Keys.GRADUAL_VOLUME] ?: true }
    suspend fun setGradualVolume(enabled: Boolean) { dataStore.edit { it[Keys.GRADUAL_VOLUME] = enabled } }

    // Haptics
    val hapticFeedback: Flow<Boolean> = dataStore.data.map { it[Keys.HAPTIC_FEEDBACK] ?: true }
    suspend fun setHapticFeedback(enabled: Boolean) { dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled } }
}
