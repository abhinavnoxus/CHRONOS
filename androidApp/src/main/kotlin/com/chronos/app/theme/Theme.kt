package com.chronos.app.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chronos.app.settings.ChronosPreferences

val ChronosTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        lineHeight = 80.sp,
        letterSpacing = (-2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)

@Composable
fun ChronosTheme(
    preferences: ChronosPreferences = ChronosPreferences.Default,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.themeMode.lowercase()) {
        "light", "ivory" -> false
        "adaptive", "system" -> systemDark
        else -> true
    }
    val accent = remember(preferences.accentArgb) { Color(preferences.accentArgb.toInt()) }
    val lowEffects = preferences.performanceMode == "saver" || preferences.animationIntensity == "reduced"
    val palette = remember(dark, accent, preferences.glassStyle, lowEffects, preferences.performanceMode) {
        createChronosPalette(dark, accent, preferences.glassStyle, lowEffects, preferences.performanceMode)
    }

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.accentPrimary,
            onPrimary = Color(0xFF211A0E),
            primaryContainer = palette.accentPrimary.copy(alpha = 0.24f),
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.accentSecondary,
            onSecondary = palette.backgroundPrimary,
            background = palette.backgroundPrimary,
            onBackground = palette.textPrimary,
            surface = palette.backgroundSecondary,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSmoked,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderSubtle,
            error = palette.statusError,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.accentPrimary,
            onPrimary = Color(0xFF211A0E),
            primaryContainer = palette.accentPrimary.copy(alpha = 0.2f),
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.accentSecondary,
            onSecondary = Color.White,
            background = palette.backgroundPrimary,
            onBackground = palette.textPrimary,
            surface = palette.backgroundSecondary,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSmoked,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderSubtle,
            error = palette.statusError,
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = palette.backgroundPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalChronosPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChronosTypography,
            content = content
        )
    }
}

/** Motion values are shared by navigation, cards, and controls. */
fun chronosMotionDuration(preferences: ChronosPreferences, standardMillis: Int = 250): Int = when {
    preferences.animationIntensity == "reduced" -> 0
    preferences.animationIntensity == "cinematic" -> (standardMillis * 1.35f).toInt().coerceAtMost(500)
    else -> standardMillis
}
