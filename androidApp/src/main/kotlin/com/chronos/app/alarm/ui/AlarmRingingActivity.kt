package com.chronos.app.alarm.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.service.AlarmPlaybackService
import com.chronos.app.theme.ChronosTheme
import com.chronos.app.theme.ChronosColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen alarm ringing activity.
 *
 * Shown on the lock screen when an alarm fires.
 * Provides:
 * - Large time display
 * - Alarm label
 * - Animated sound waves
 * - Swipe-to-dismiss slider
 * - Snooze button
 */
class AlarmRingingActivity : ComponentActivity() {

    private var alarmId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow showing on lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Keep screen on while alarm is ringing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: ""
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, 0)

        setContent {
            ChronosTheme {
                AlarmRingingScreen(
                    alarmLabel = label,
                    hour = hour,
                    minute = minute,
                    onDismiss = { dismissAlarm() },
                    onSnooze = { snoozeAlarm() }
                )
            }
        }
    }

    private fun dismissAlarm() {
        AlarmPlaybackService.instance?.dismissAlarm()
        finish()
    }

    private fun snoozeAlarm() {
        val intent = android.content.Intent(this, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_SNOOZE_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        startForegroundService(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent dismissing by back button — user must swipe or tap snooze
    }
}

@Composable
fun AlarmRingingScreen(
    alarmLabel: String,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val timeText = String.format("%02d:%02d", hour, minute)
    val dateFormat = SimpleDateFormat("EEEE · MMMM d", Locale.getDefault())
    val dateText = dateFormat.format(Date()).uppercase()

    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_bg")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )

    // Sound wave animation
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Pulse animation for the alarm icon
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ChronosColors.AlarmBackgroundPrimary,
                        ChronosColors.AlarmBackgroundSecondary,
                        ChronosColors.AlarmBackgroundTertiary
                    ),
                    center = Offset(0.5f, gradientShift),
                    radius = 1000f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Pulsing alarm icon
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier
                    .size((64 * pulseScale).dp)
                    .alpha(0.9f),
                tint = ChronosColors.ChampagneGold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Alarm label
            if (alarmLabel.isNotEmpty()) {
                Text(
                    text = alarmLabel.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = ChronosColors.ChampagneGold.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Large time display
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Date
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated sound waves
            SoundWaves(phase = wavePhase)

            Spacer(modifier = Modifier.weight(1f))

            // Swipe to dismiss slider
            SwipeToDismissSlider(
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Snooze button
            SnoozeButton(onSnooze = onSnooze)

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun SoundWaves(phase: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 24) {
            val height = (20 + 30 * kotlin.math.abs(kotlin.math.sin(phase + i * 0.4))).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        ChronosColors.ChampagneGold.copy(
                            alpha = 0.3f + 0.4f * kotlin.math.abs(kotlin.math.sin(phase + i * 0.4)).toFloat()
                        )
                    )
            )
        }
    }
}

@Composable
fun SwipeToDismissSlider(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val maxOffset = with(density) { screenWidth.toPx() - 80f }
    val dismissThreshold = maxOffset * 0.7f

    val progress = (dragOffset / maxOffset).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ChronosColors.GlassSurface.copy(alpha = 0.3f),
                        ChronosColors.GlassSurface.copy(alpha = 0.1f + 0.3f * progress)
                    )
                )
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset >= dismissThreshold) {
                            onDismiss()
                        } else {
                            dragOffset = 0f
                        }
                    },
                    onDragCancel = { dragOffset = 0f }
                ) { _, dragAmount ->
                    dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxOffset)
                }
            }
    ) {
        // Track text
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Swipe to dismiss →",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.5f - 0.3f * progress),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Glow effect following thumb
        Box(
            modifier = Modifier
                .offset(x = dragOffset.toDp())
                .size(72.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = ChronosColors.ChampagneGold.copy(alpha = 0.4f * progress),
                    spotColor = ChronosColors.ChampagneGold.copy(alpha = 0.4f * progress)
                )
        )

        // Thumb
        Box(
            modifier = Modifier
                .offset(x = dragOffset.toDp())
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ChronosColors.ChampagneGold.copy(alpha = 0.8f + 0.2f * progress),
                            ChronosColors.ChampagneGold.copy(alpha = 0.6f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun SnoozeButton(onSnooze: () -> Unit) {
    Button(
        onClick = onSnooze,
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ChronosColors.GlassSurface.copy(alpha = 0.2f)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Snooze,
            contentDescription = null,
            tint = ChronosColors.ChampagneGold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Snooze",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
