package com.chronos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.app.theme.LocalChronosPalette

/** Shared hierarchy, states and spacing. No per-screen dashboard templates or realtime blur. */
@Composable
fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val palette = LocalChronosPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = palette.textPrimary)
            Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = palette.textTertiary)
        }
        action?.invoke()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp),
        color = LocalChronosPalette.current.textTertiary)
}

@Composable
fun StatePanel(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    loading: Boolean = false
) {
    val palette = LocalChronosPalette.current
    GlassCard(modifier.fillMaxWidth(), style = GlassStyle.FROSTED, cornerRadius = 24.dp, padding = 24.dp) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = palette.accentPrimary, strokeWidth = 2.dp)
        else Icon(icon, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun AdaptiveContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp), content = content)
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalChronosPalette.current
    Column(modifier.padding(vertical = 4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.accentPrimary)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = palette.textTertiary)
    }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null) {
    val palette = LocalChronosPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = palette.statusError)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
val SectionSpacing = 16.dp
