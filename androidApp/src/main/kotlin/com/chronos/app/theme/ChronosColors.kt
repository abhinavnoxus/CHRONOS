package com.chronos.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colours used by every CHRONOS surface. */
@Immutable
data class ChronosPalette(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val surfaceGlass: Color,
    val surfaceGlassElevated: Color,
    val surfaceSmoked: Color,
    val surfaceFrosted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val borderSubtle: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean,
    val lowEffects: Boolean,
    val performanceMode: String
)

val LocalChronosPalette = staticCompositionLocalOf {
    ChronosPalette(
        backgroundPrimary = Color(0xFF101114),
        backgroundSecondary = Color(0xFF17191E),
        surfaceGlass = Color.White.copy(alpha = 0.07f),
        surfaceGlassElevated = Color.White.copy(alpha = 0.11f),
        surfaceSmoked = Color(0xFF17191E).copy(alpha = 0.94f),
        surfaceFrosted = Color.White.copy(alpha = 0.12f),
        textPrimary = Color(0xFFF5F2EC),
        textSecondary = Color(0xFFB8B7B3),
        textTertiary = Color(0xFF85858A),
        accentPrimary = Color(0xFFC6A36B),
        accentSecondary = Color(0xFFA9BDD1),
        borderSubtle = Color.White.copy(alpha = 0.12f),
        statusSuccess = Color(0xFF72B69A),
        statusWarning = Color(0xFFE4B36B),
        statusError = Color(0xFFE27E7E),
        isDark = true,
        lowEffects = false,
        performanceMode = "balanced"
    )
}

/** Legacy constants retained for alarm-specific code; new UI should use [LocalChronosPalette]. */
object ChronosColors {
    val ChampagneGold = Color(0xFFC6A36B)
    val ChampagneGoldLight = Color(0xFFDCC08E)
    val ChampagneGoldDark = Color(0xFFA98951)
    val MutedBlue = Color(0xFFA9BDD1)
    val MutedBlueLight = Color(0xFFC5D4E1)
    val MutedBlueDark = Color(0xFF71879D)
    val CharcoalDark = Color(0xFF101114)
    val CharcoalMedium = Color(0xFF17191E)
    val CharcoalLight = Color(0xFF252830)
    val GlassSurface = Color.White
    val GlassSurfaceDark = Color(0xFF17191E)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
    val GlassBorderLight = Color.White.copy(alpha = 0.2f)
    val AlarmBackgroundPrimary = Color(0xFF241A11)
    val AlarmBackgroundSecondary = Color(0xFF101114)
    val AlarmBackgroundTertiary = Color(0xFF17191E)
    val TextPrimary = Color(0xFFF5F2EC)
    val TextSecondary = Color(0xFFB8B7B3)
    val TextTertiary = Color(0xFF85858A)
    val Success = Color(0xFF72B69A)
    val Warning = Color(0xFFE4B36B)
    val Error = Color(0xFFE27E7E)
    val LightBackground = Color(0xFFEDE8DF)
    val LightSurface = Color(0xFFF8F5EF)
    val LightText = Color(0xFF242321)
    val LightTextSecondary = Color(0xFF66645F)
}

internal fun createChronosPalette(
    dark: Boolean,
    accent: Color,
    glassStyle: String,
    lowEffects: Boolean,
    performanceMode: String
): ChronosPalette {
    val background = if (dark) Color(0xFF101114) else Color(0xFFEDE8DF)
    val secondary = if (dark) Color(0xFF17191E) else Color(0xFFF7F4EE)
    val isAmoled = dark && glassStyle.equals("amoled", ignoreCase = true)
    val bg = if (isAmoled) Color.Black else background
    val glassAlpha = when (glassStyle.lowercase()) {
        "crystal" -> if (dark) 0.09f else 0.68f
        "frosted" -> if (dark) 0.13f else 0.76f
        "smoked" -> if (dark) 0.12f else 0.84f
        else -> if (dark) 0.07f else 0.72f
    }
    val textPrimary = if (dark) Color(0xFFF5F2EC) else Color(0xFF242321)
    val textSecondary = if (dark) Color(0xFFB8B7B3) else Color(0xFF66645F)
    val textTertiary = if (dark) Color(0xFF85858A) else Color(0xFF77736C)
    val border = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xFF5B5448).copy(alpha = 0.15f)

    return ChronosPalette(
        backgroundPrimary = bg,
        backgroundSecondary = secondary,
        surfaceGlass = if (dark) Color.White.copy(alpha = glassAlpha) else Color.White.copy(alpha = glassAlpha),
        surfaceGlassElevated = if (dark) Color.White.copy(alpha = glassAlpha + 0.04f) else Color.White.copy(alpha = 0.9f),
        surfaceSmoked = if (dark) Color(0xFF17191E).copy(alpha = 0.94f) else Color(0xFFE5DFD4).copy(alpha = 0.9f),
        surfaceFrosted = if (dark) Color(0xFFB9C1CA).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.82f),
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        accentPrimary = accent,
        accentSecondary = if (dark) Color(0xFFA9BDD1) else Color(0xFF546B82),
        borderSubtle = border,
        statusSuccess = if (dark) Color(0xFF72B69A) else Color(0xFF397A60),
        statusWarning = if (dark) Color(0xFFE4B36B) else Color(0xFF94651D),
        statusError = if (dark) Color(0xFFE27E7E) else Color(0xFFAC4545),
        isDark = dark,
        lowEffects = lowEffects,
        performanceMode = performanceMode
    )
}
