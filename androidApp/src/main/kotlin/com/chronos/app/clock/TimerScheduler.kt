package com.chronos.app.clock

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.Timer
import com.chronos.app.di.ApplicationScope
import com.chronos.app.notifications.NotificationHelper
import com.chronos.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = context.getSystemService(AlarmManager::class.java)
    fun schedule(timer: Timer): Boolean {
        cancel(timer.id)
        if (!timer.isRunning || timer.startedAt == null) return false
        val at = timer.startedAt + timer.remainingDuration
        if (at <= System.currentTimeMillis()) return false
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION_TIMER; data = Uri.parse("chronos://timer/${timer.id}")
            putExtra(EXTRA_ID, timer.id); putExtra(EXTRA_AT, at); putExtra(EXTRA_LABEL, timer.label)
        }
        val pending = PendingIntent.getBroadcast(context, code(timer.id), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }.isSuccess
    }
    fun cancel(id: Long) {
        PendingIntent.getBroadcast(context, code(id), Intent(context, TimerReceiver::class.java).apply { action = ACTION_TIMER; data = Uri.parse("chronos://timer/$id") },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { manager.cancel(it); it.cancel() }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + (id % 100000).toInt())
    }
    companion object {
        const val ACTION_TIMER = "com.chronos.action.TIMER_DONE"
        const val EXTRA_ID = "timer_id"; const val EXTRA_AT = "timer_at"; const val EXTRA_LABEL = "timer_label"
        const val NOTIFICATION_BASE = 26000000
        fun code(id: Long) = ((id xor (id ushr 32)).toInt() xor 0x544d4552) and 0x7fffffff
    }
}

@AndroidEntryPoint
class TimerReceiver : BroadcastReceiver() {
    @Inject lateinit var database: ChronosDatabase
    @Inject lateinit var scheduler: TimerScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(TimerScheduler.EXTRA_ID, -1L)
        val at = intent.getLongExtra(TimerScheduler.EXTRA_AT, 0L)
        if (id < 0L) return
        val result = goAsync()
        scope.launch {
            try {
                val timer = database.timerDao().getAllTimersSnapshot().find { it.id == id } ?: return@launch
                if (!timer.isRunning || timer.startedAt == null || timer.startedAt + timer.remainingDuration != at) return@launch
                database.timerDao().updateTimer(timer.copy(isRunning = false, isPaused = false, remainingDuration = 0L, startedAt = null))
                val open = PendingIntent.getActivity(context, TimerScheduler.code(id), Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW; data = Uri.parse("chronos://app/tools")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_TIMER)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(timer.label.ifBlank { "Timer complete" })
                    .setContentText("Your countdown has finished.").setAutoCancel(true).setContentIntent(open)
                    .setCategory(NotificationCompat.CATEGORY_ALARM).build()
                runCatching { context.getSystemService(NotificationManager::class.java).notify(TimerScheduler.NOTIFICATION_BASE + (id % 100000).toInt(), notification) }
            } finally { result.finish() }
        }
    }
}
