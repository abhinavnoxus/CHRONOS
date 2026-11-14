package com.chronos.app.ui.screens.clock

import android.text.format.DateFormat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.app.theme.ChronosColors
import com.chronos.app.ui.components.GlassCard
import com.chronos.app.ui.components.GlassStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClockScreen() {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }

    val hour = if (is24Hour) calendar.get(Calendar.HOUR_OF_DAY)
    else {
        val h = calendar.get(Calendar.HOUR)
        if (h == 0) 12 else h
    }
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)

    val dateFormat = remember { SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()) }
    val dateText = remember(currentTime / 86400000L) { dateFormat.format(Date(currentTime)).uppercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Date
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Normal
            ),
            color = ChronosColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Analog clock
        AnalogClock(
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = minute,
            second = second,
            modifier = Modifier.size(220.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Digital time
        Text(
            text = buildTimeString(hour, minute, second, is24Hour),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp
            ),
            color = ChronosColors.TextPrimary
        )

        // Seconds display
        Text(
            text = String.format(":%02d", second),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Light,
                color = ChronosColors.ChampagneGold
            ),
            modifier = Modifier.offset(y = (-16).dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Quick info cards
        GlassCard(
            style = GlassStyle.LUXURY,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "NEXT ALARM",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = ChronosColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "07:30",
                style = MaterialTheme.typography.headlineLarge,
                color = ChronosColors.ChampagneGold
            )
            Text(
                text = "Tomorrow · Monday",
                style = MaterialTheme.typography.bodyMedium,
                color = ChronosColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassCard(
            style = GlassStyle.LUXURY,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "UPCOMING",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = ChronosColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Team Standup",
                style = MaterialTheme.typography.titleLarge,
                color = ChronosColors.TextPrimary
            )
            Text(
                text = "10:00 AM · Google Meet",
                style = MaterialTheme.typography.bodyMedium,
                color = ChronosColors.TextSecondary
            )
        }
    }
}

private fun buildTimeString(hour: Int, minute: Int, second: Int, is24Hour: Boolean): String {
    return if (is24Hour) {
        String.format("%02d:%02d", hour, minute)
    } else {
        String.format("%d:%02d", hour, minute)
    }
}

@Composable
fun AnalogClock(
    hour: Int,
    minute: Int,
    second: Int,
    modifier: Modifier = Modifier
) {
    val champagneGold = ChronosColors.ChampagneGold
    val textSecondary = ChronosColors.TextSecondary
    val glassBorder = ChronosColors.GlassBorder

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2

        // Outer ring
        drawCircle(
            color = glassBorder,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f)
        )

        // Hour markers
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val startRadius = radius * 0.85f
            val endRadius = radius * 0.95f
            drawLine(
                color = if (i % 3 == 0) champagneGold else textSecondary.copy(alpha = 0.5f),
                start = Offset(
                    centerX + (startRadius * cos(angle)).toFloat(),
                    centerY + (startRadius * sin(angle)).toFloat()
                ),
                end = Offset(
                    centerX + (endRadius * cos(angle)).toFloat(),
                    centerY + (endRadius * sin(angle)).toFloat()
                ),
                strokeWidth = if (i % 3 == 0) 3f else 1.5f,
                cap = StrokeCap.Round
            )
        }

        // Hour hand
        val hourAngle = Math.toRadians(((hour % 12 + minute / 60f) * 30 - 90).toDouble())
        drawLine(
            color = champagneGold,
            start = Offset(centerX, centerY),
            end = Offset(
                centerX + (radius * 0.5f * cos(hourAngle)).toFloat(),
                centerY + (radius * 0.5f * sin(hourAngle)).toFloat()
            ),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Minute hand
        val minuteAngle = Math.toRadians(((minute + second / 60f) * 6 - 90).toDouble())
        drawLine(
            color = champagneGold.copy(alpha = 0.8f),
            start = Offset(centerX, centerY),
            end = Offset(
                centerX + (radius * 0.7f * cos(minuteAngle)).toFloat(),
                centerY + (radius * 0.7f * sin(minuteAngle)).toFloat()
            ),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )

        // Second hand
        val secondAngle = Math.toRadians((second * 6 - 90).toDouble())
        drawLine(
            color = Color(0xFFE53935),
            start = Offset(centerX, centerY),
            end = Offset(
                centerX + (radius * 0.75f * cos(secondAngle)).toFloat(),
                centerY + (radius * 0.75f * sin(secondAngle)).toFloat()
            ),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )

        // Center dot
        drawCircle(
            color = champagneGold,
            radius = 5f,
            center = Offset(centerX, centerY)
        )
    }
}
