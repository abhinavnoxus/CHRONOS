package com.chronos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronos.app.theme.LocalChronosPalette

/** Reusable surface treatments; blur is intentionally avoided on scrolling content. */
enum class GlassStyle { LUXURY, SMOKED, FROSTED }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.LUXURY,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalChronosPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val surface = when (style) {
        GlassStyle.LUXURY -> palette.surfaceGlass
        GlassStyle.SMOKED -> palette.surfaceSmoked
        GlassStyle.FROSTED -> palette.surfaceFrosted
    }
    val border = when (style) {
        GlassStyle.LUXURY -> palette.accentPrimary.copy(alpha = if (palette.isDark) 0.2f else 0.28f)
        GlassStyle.SMOKED -> palette.borderSubtle
        GlassStyle.FROSTED -> palette.borderSubtle.copy(alpha = 1f)
    }
    val elevation = when {
        palette.lowEffects || palette.performanceMode == "saver" -> 0.dp
        palette.performanceMode == "balanced" -> if (style == GlassStyle.LUXURY) 2.dp else 1.dp
        style == GlassStyle.LUXURY -> 4.dp
        else -> 2.dp
    }

    Column(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(
                brush = if (palette.lowEffects) {
                    Brush.verticalGradient(listOf(surface, surface))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            surface.copy(alpha = (surface.alpha + 0.035f).coerceAtMost(1f)),
                            surface
                        )
                    )
                },
                shape = shape
            )
            .border(1.dp, border, shape)
            .padding(padding)
    ) {
        content()
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalChronosPalette.current
    SpacerSurface(modifier = modifier, shape = shape, color = if (elevated) palette.surfaceGlassElevated else palette.surfaceGlass, content = content)
}

@Composable
private fun SpacerSurface(
    modifier: Modifier,
    shape: Shape,
    color: Color,
    content: @Composable BoxScope.() -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(color, shape)
            .border(1.dp, LocalChronosPalette.current.borderSubtle, shape),
        content = content
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val palette = LocalChronosPalette.current
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.borderSubtle)
    )
}
