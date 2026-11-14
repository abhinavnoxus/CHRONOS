package com.chronos.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronos.app.theme.ChronosColors

/**
 * CHRONOS Glass Card Components
 *
 * Three glass styles:
 * - Luxury Glass: Clear, reflective, champagne gold border
 * - Smoked Glass: Darker, more opaque
 * - Frosted Glass: Blurred background, diffused
 */

enum class GlassStyle {
    LUXURY, SMOKED, FROSTED
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.LUXURY,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val (backgroundColor, borderColor, borderAlpha) = when (style) {
        GlassStyle.LUXURY -> Triple(
            ChronosColors.GlassSurface.copy(alpha = 0.06f),
            ChronosColors.ChampagneGold,
            0.15f
        )
        GlassStyle.SMOKED -> Triple(
            ChronosColors.GlassSurfaceDark.copy(alpha = 0.7f),
            ChronosColors.GlassBorder,
            1f
        )
        GlassStyle.FROSTED -> Triple(
            ChronosColors.GlassSurface.copy(alpha = 0.1f),
            ChronosColors.GlassBorderLight,
            1f
        )
    }

    Column(
        modifier = modifier
            .shadow(
                elevation = if (style == GlassStyle.LUXURY) 4.dp else 2.dp,
                shape = shape,
                ambientColor = if (style == GlassStyle.LUXURY)
                    ChronosColors.ChampagneGold.copy(alpha = 0.1f)
                else Color.Transparent,
                spotColor = if (style == GlassStyle.LUXURY)
                    ChronosColors.ChampagneGold.copy(alpha = 0.05f)
                else Color.Transparent
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = backgroundColor.alpha * 0.7f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = borderAlpha),
                shape = shape
            )
            .padding(padding)
    ) {
        content()
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    alpha: Float = 0.05f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                ChronosColors.GlassSurface.copy(alpha = alpha),
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Subtle glass divider line
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    color: Color = ChronosColors.GlassBorder
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}
