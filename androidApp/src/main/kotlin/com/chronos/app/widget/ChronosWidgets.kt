package com.chronos.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.chronos.app.R
import com.chronos.app.alarm.engine.AlarmTimeCalculator
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.di.ApplicationScope
import com.chronos.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Shared renderer for three lightweight, no-background-service home-screen widgets. */
@AndroidEntryPoint
class ChronosWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var database: ChronosDatabase
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        scope.launch {
            try {
                val next = database.alarmDao().getEnabledAlarmsSync().mapNotNull { alarm ->
                    AlarmTimeCalculator.calculateNextTriggerTime(alarm)?.let { alarm.label.ifBlank { "Alarm" } to it }
                }.minByOrNull { it.second }
                val now = System.currentTimeMillis()
                val events = database.calendarEventDao().getUpcomingSnapshot(now).take(3)
                ids.forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    val wide = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) >= 220
                    val views = RemoteViews(context.packageName, if (wide) R.layout.widget_chronos else R.layout.widget_chronos_compact)
                    views.setTextViewText(R.id.widget_date, SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now)))
                    views.setTextViewText(R.id.widget_next_alarm, next?.let { "${it.first} · ${SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(it.second))}" } ?: "No alarm scheduled")
                    if (wide) views.setTextViewText(R.id.widget_agenda, events.joinToString("\n") { "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.startTime))}  ${it.title}" }.ifBlank { "No cached upcoming events" })
                    val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW; data = Uri.parse("chronos://app/home")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root, tap)
                    manager.updateAppWidget(id, views)
                }
            } catch (exception: Exception) {
                android.util.Log.e("ChronosWidget", "Widget update failed", exception)
            } finally { result.finish() }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val intent = Intent(context, ChronosWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
            context.sendBroadcast(intent)
        }
    }
}
