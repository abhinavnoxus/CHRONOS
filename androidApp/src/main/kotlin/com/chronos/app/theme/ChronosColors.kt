package com.chronos.app.theme

import androidx.compose.ui.graphics.Color

/**
 * CHRONOS Color System
 *
 * Luxury Adaptive Glass design language.
 * Primary: Champagne Gold
 * Secondary: Muted Blue
 * Background: Near-black charcoal
 */
object ChronosColors {
    // Primary accent — Champagne Gold
    val ChampagneGold = Color(0xFFD4A853)
    val ChampagneGoldLight = Color(0xFFE8C97A)
    val ChampagneGoldDark = Color(0xFFB8903E)

    // Secondary accent — Muted Blue
    val MutedBlue = Color(0xFF5B7FA5)
    val MutedBlueLight = Color(0xFF7A9FC5)
    val MutedBlueDark = Color(0xFF3D5F85)

    // Backgrounds — Near-black charcoal
    val CharcoalDark = Color(0xFF0D0D0F)
    val CharcoalMedium = Color(0xFF1A1A1E)
    val CharcoalLight = Color(0xFF2A2A2E)

    // Glass surfaces
    val GlassSurface = Color(0xFFFFFFFF)
    val GlassSurfaceDark = Color(0xFF1E1E22)
    val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)
    val GlassBorderLight = Color(0xFFFFFFFF).copy(alpha = 0.15f)

    // Alarm ringing backgrounds
    val AlarmBackgroundPrimary = Color(0xFF1A0A00)
    val AlarmBackgroundSecondary = Color(0xFF0D0D0F)
    val AlarmBackgroundTertiary = Color(0xFF1A1520)

    // Text
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFA0A0A5)
    val TextTertiary = Color(0xFF6A6A70)

    // Semantic
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFE53935)

    // Light theme
    val LightBackground = Color(0xFFF5F3EF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightText = Color(0xFF1A1A1E)
    val LightTextSecondary = Color(0xFF5A5A60)
}
