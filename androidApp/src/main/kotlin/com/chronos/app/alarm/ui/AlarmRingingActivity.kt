package com.chronos.app.alarm.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.alarm.receiver.AlarmDismissReceiver
import com.chronos.app.alarm.receiver.AlarmSnoozeReceiver
import com.chronos.app.settings.ChronosPreferences
import com.chronos.app.settings.ChronosSettings
import com.chronos.app.theme.ChronosTheme
import com.chronos.app.theme.LocalChronosPalette
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {
    @Inject lateinit var chronosSettings: ChronosSettings

    private var ringingAlarm by mutableStateOf<RingingAlarm?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activeActivity = this
        ringingAlarm = intent.toRingingAlarm()

        setContent {
            val preferences by chronosSettings.preferences.collectAsStateWithLifecycle(
                initialValue = ChronosPreferences.Default
            )
            ChronosTheme(preferences) {
                val alarm = ringingAlarm
                if (alarm != null) {
                    AlarmRingingScreen(
                        alarmLabel = alarm.label,
                        is24Hour = preferences.is24Hour,
                        onDismiss = { dispatchDismiss(alarm) },
                        onSnooze = { dispatchSnooze(alarm) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ringingAlarm = intent.toRingingAlarm()
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun Intent.toRingingAlarm(): RingingAlarm? {
        val id = getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        if (id < 0L || instanceId.isBlank()) return null
        return RingingAlarm(
            id = id,
            instanceId = instanceId,
            scheduledAt = getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L),
            isSnooze = getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false),
            label = getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
        )
    }

    private fun dispatchDismiss(alarm: RingingAlarm) {
        sendBroadcast(Intent(this, AlarmDismissReceiver::class.java).apply {
            action = "com.chronos.action.DISMISS_ALARM"
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, alarm.instanceId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, alarm.scheduledAt)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, alarm.isSnooze)
        })
        finish()
    }

    private fun dispatchSnooze(alarm: RingingAlarm) {
        sendBroadcast(Intent(this, AlarmSnoozeReceiver::class.java).apply {
            action = "com.chronos.action.SNOOZE_ALARM"
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmScheduler.EXTRA_INSTANCE_ID, alarm.instanceId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, alarm.scheduledAt)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, alarm.isSnooze)
        })
    }

    override fun onDestroy() {
        if (activeActivity === this) activeActivity = null
        super.onDestroy()
    }

    private data class RingingAlarm(
        val id: Long,
        val instanceId: String,
        val scheduledAt: Long,
        val isSnooze: Boolean,
        val label: String
    )

    companion object {
        @Volatile private var activeActivity: AlarmRingingActivity? = null

        fun finishForInstance(instanceId: String) {
            val activity = activeActivity ?: return
            if (activity.ringingAlarm?.instanceId == instanceId) {
                activity.runOnUiThread { if (!activity.isFinishing) activity.finish() }
            }
        }
    }
}

@Composable
private fun AlarmRingingScreen(
    alarmLabel: String,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val palette = LocalChronosPalette.current
    var now by androidx.compose.runtime.remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val time = rememberAlarmTime(now, is24Hour)
    val date = rememberAlarmDate(now)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.backgroundSecondary, palette.backgroundPrimary, palette.backgroundPrimary)
                )
            )
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = palette.accentPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(24.dp))
            if (alarmLabel.isNotBlank()) {
                Text(
                    text = alarmLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accentPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = time,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                    fontSize = 76.sp,
                    letterSpacing = (-2).sp
                ),
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.labelLarge,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            AlarmSoundMark(color = palette.accentPrimary)
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentPrimary,
                    contentColor = Color(0xFF211A0E)
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Dismiss alarm", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Snooze, contentDescription = null, tint = palette.accentPrimary)
                Spacer(Modifier.size(10.dp))
                Text("Snooze", color = palette.textPrimary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AlarmSoundMark(color: Color) {
    val heights = listOf(18, 28, 42, 54, 38, 28, 46, 24, 34, 50, 28, 18)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { index, height ->
            Spacer(
                modifier = Modifier
                    .size(width = 4.dp, height = height.dp)
                    .background(color.copy(alpha = if (index % 3 == 0) 0.95f else 0.54f), CircleShape)
            )
        }
    }
}

@Composable
private fun rememberAlarmTime(now: Long, is24Hour: Boolean): String =
    androidx.compose.runtime.remember(now / 1_000L, is24Hour) {
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(now))
    }

@Composable
private fun rememberAlarmDate(now: Long): String = androidx.compose.runtime.remember(now / 86_400_000L) {
    SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()).format(Date(now))
}
