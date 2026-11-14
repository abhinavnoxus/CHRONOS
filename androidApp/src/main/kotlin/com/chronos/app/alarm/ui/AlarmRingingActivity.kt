package com.chronos.app.alarm.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.alarm.engine.AlarmIdentity
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.alarm.engine.AlarmScheduler
import com.chronos.app.di.ApplicationScope
import com.chronos.app.settings.ChronosPreferences
import com.chronos.app.settings.ChronosSettings
import com.chronos.app.theme.ChronosTheme
import com.chronos.app.theme.LocalChronosPalette
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Lock-screen UI and the no-trampoline notification dismissal entry point. */
@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {
    @Inject lateinit var chronosSettings: ChronosSettings
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    private var ringingAlarm by mutableStateOf<RingingAlarm?>(null)
    private var status by mutableStateOf<String?>(null)
    private val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID) == ringingAlarm?.instanceId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handleIntent(intent)
        if (isFinishing) return
        val filter = IntentFilter(AlarmRuntimeController.ACTION_INSTANCE_FINISHED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(finishedReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(finishedReceiver, filter)
        setContent {
            val preferences by chronosSettings.preferences.collectAsStateWithLifecycle(initialValue = ChronosPreferences.Default)
            ChronosTheme(preferences) {
                val alarm = ringingAlarm
                if (alarm != null) {
                    LaunchedEffect(alarm.instanceId) {
                        if (!runtimeController.isInstanceRinging(alarm.id, alarm.instanceId)) finish()
                    }
                    RingingScreen(alarm.label, preferences.is24Hour, status,
                        onDismiss = { dismiss(alarm) }, onSnooze = { snooze(alarm) })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val alarm = intent.toRingingAlarm()
        if (alarm == null) { finish(); return }
        if (intent.action == ACTION_DISMISS_FROM_NOTIFICATION) {
            dismiss(alarm)
            return
        }
        ringingAlarm = alarm
        status = null
    }

    private fun Intent.toRingingAlarm(): RingingAlarm? {
        val id = getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val instanceId = getStringExtra(AlarmScheduler.EXTRA_INSTANCE_ID).orEmpty()
        val scheduledAt = getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        if (!AlarmIdentity.matches(id, instanceId, scheduledAt, isSnooze)) return null
        return RingingAlarm(id, instanceId, scheduledAt, isSnooze, getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty())
    }

    private fun dismiss(alarm: RingingAlarm) {
        val owned = runtimeController.stopImmediately(alarm.id, alarm.instanceId)
        applicationScope.launch {
            try { runtimeController.dismiss(alarm.id, alarm.instanceId, alarm.scheduledAt, alarm.isSnooze, ownershipProven = owned) }
            finally { runOnUiThread { finish() } }
        }
        finish()
    }

    private fun snooze(alarm: RingingAlarm) {
        status = "Scheduling snooze…"
        applicationScope.launch {
            val success = try { runtimeController.snooze(alarm.id, alarm.instanceId, alarm.scheduledAt, alarm.isSnooze) } catch (_: Exception) { false }
            runOnUiThread {
                if (success) finish() else status = "Snooze could not be scheduled. The alarm is still active; you can stop it."
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(finishedReceiver) }
        super.onDestroy()
    }

    private data class RingingAlarm(val id: Long, val instanceId: String, val scheduledAt: Long, val isSnooze: Boolean, val label: String)
    companion object {
        const val ACTION_DISMISS_FROM_NOTIFICATION = "com.chronos.action.DISMISS_FROM_NOTIFICATION"
    }
}

@Composable
private fun RingingScreen(label: String, is24Hour: Boolean, status: String?, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val palette = LocalChronosPalette.current
    var now by androidx.compose.runtime.remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000L) } }
    Box(Modifier.fillMaxSize().background(palette.backgroundPrimary).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Alarm, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text(label.ifBlank { "Your alarm" }, style = MaterialTheme.typography.titleLarge, color = palette.accentPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(now)),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 72.sp),
                color = palette.textPrimary)
            Text(SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()).format(Date(now)), style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
            Spacer(Modifier.height(48.dp))
            // Critical actions are never gated by an animation or transition.
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Close, null); Spacer(Modifier.size(10.dp)); Text("Stop alarm", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.Snooze, null); Spacer(Modifier.size(10.dp)); Text("Snooze", style = MaterialTheme.typography.titleMedium)
            }
            if (status != null) {
                Spacer(Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = palette.statusWarning, textAlign = TextAlign.Center)
            }
        }
    }
}
