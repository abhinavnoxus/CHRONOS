package com.chronos.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chronos.app.R
import com.chronos.app.data.model.Reminder
import com.chronos.app.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CHRONOS Notification Manager
 *
 * Manages all non-alarm notifications (reminders, suggestions).
 * Alarm notifications are handled by AlarmPlaybackService.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_REMINDER = "chronos_reminder_channel"
        const val CHANNEL_SUGGESTION = "chronos_suggestion_channel"
        const val CHANNEL_MISSED = "chronos_missed_channel"

        private const val REMINDER_NOTIFICATION_BASE = 20000
        private const val SUGGESTION_NOTIFICATION_BASE = 30000
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_REMINDER,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminder notifications"
            },
            NotificationChannel(
                CHANNEL_SUGGESTION,
                "Smart Suggestions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Calendar-aware alarm suggestions"
            },
            NotificationChannel(
                CHANNEL_MISSED,
                "Missed Alarms",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Report of missed alarms"
            }
        )

        channels.forEach { notificationManager.createNotificationChannel(it) }
    }

    fun showReminderNotification(reminder: Reminder) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (REMINDER_NOTIFICATION_BASE + reminder.id).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.description.ifEmpty { "CHRONOS Reminder" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(
            (REMINDER_NOTIFICATION_BASE + reminder.id).toInt(),
            notification
        )
    }

    fun showSmartSuggestion(title: String, message: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUGGESTION_NOTIFICATION_BASE + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SUGGESTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("CHRONOS Suggestion")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(SUGGESTION_NOTIFICATION_BASE + id, notification)
    }

    fun showMissedAlarmReport(missedCount: Int, alarmLabels: List<String>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            40000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (missedCount == 1) {
            "You missed: ${alarmLabels.firstOrNull() ?: "an alarm"}"
        } else {
            "You missed $missedCount alarms"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Missed Alarms")
            .setContentText(text)
            .setSubText("CHRONOS")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(40000, notification)
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
