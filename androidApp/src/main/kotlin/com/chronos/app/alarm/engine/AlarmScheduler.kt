package com.chronos.app.alarm.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.chronos.app.alarm.receiver.AlarmTriggerReceiver
import com.chronos.app.data.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CHRONOS Alarm Scheduler
 *
 * Handles exact alarm scheduling using AlarmManager.
 * Supports:
 * - One-time alarms
 * - Recurring daily alarms (specific days of week)
 * - Reboot recovery
 * - Timezone changes
 * - DST transitions
 *
 * Uses SCHEDULE_EXACT_ALARM permission on Android 12+.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_ALARM_HOUR = "extra_alarm_hour"
        const val EXTRA_ALARM_MINUTE = "extra_alarm_minute"
        const val EXTRA_ALARM_RINGTONE = "extra_alarm_ringtone"
        const val EXTRA_ALARM_VOLUME = "extra_alarm_volume"
        const val EXTRA_ALARM_VIBRATE = "extra_alarm_vibrate"
        const val EXTRA_ALARM_SNOOZE_MINUTES = "extra_alarm_snooze_minutes"
        const val EXTRA_ALARM_GRADUAL_VOLUME = "extra_alarm_gradual_volume"
        const val EXTRA_ALARM_CHALLENGE_TYPE = "extra_alarm_challenge_type"

        // Base request code offset to avoid collisions with other PendingIntents
        private const val REQUEST_CODE_BASE = 10000
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule an alarm. Calculates the next trigger time and sets an exact alarm.
     */
    fun scheduleAlarm(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return
        }

        val triggerTime = calculateNextTriggerTime(alarm)
        if (triggerTime == null) {
            Log.w(TAG, "No future trigger time for alarm ${alarm.id}, cancelling")
            cancelAlarm(alarm.id)
            return
        }

        val pendingIntent = createPendingIntent(alarm)

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, getShowAlarmPendingIntent()),
                    pendingIntent
                )
                Log.i(TAG, "Scheduled alarm ${alarm.id} for ${java.util.Date(triggerTime)}")
            } else {
                Log.w(TAG, "Cannot schedule exact alarms — permission not granted")
                // Fall back to inexact alarm (less reliable)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
        }
    }

    /**
     * Cancel a scheduled alarm.
     */
    fun cancelAlarm(alarmId: Long) {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            generateRequestCode(alarmId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.i(TAG, "Cancelled alarm $alarmId")
        }
    }

    /**
     * Reschedule all enabled alarms (used after boot, timezone change, etc.)
     */
    fun rescheduleAllAlarms(alarms: List<Alarm>) {
        Log.i(TAG, "Rescheduling ${alarms.size} alarms")
        alarms.filter { it.isEnabled }.forEach { scheduleAlarm(it) }
    }

    /**
     * Calculate the next trigger time for an alarm.
     */
    fun calculateNextTriggerTime(alarm: Alarm): Long? {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If it's a one-time alarm on a specific date
        if (alarm.oneTimeDate != null) {
            val dateCal = Calendar.getInstance().apply {
                timeInMillis = alarm.oneTimeDate
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return if (dateCal.timeInMillis > now) dateCal.timeInMillis else null
        }

        // Recurring alarm
        if (alarm.isRepeating) {
            return calculateNextRepeatingTrigger(alarm, calendar, now)
        }

        // One-time alarm (next occurrence)
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return if (calendar.timeInMillis > now) calendar.timeInMillis else null
    }

    private fun calculateNextRepeatingTrigger(
        alarm: Alarm,
        calendar: Calendar,
        now: Long
    ): Long? {
        // Check next 8 days to find the next valid trigger
        for (dayOffset in 0..7) {
            val candidate = calendar.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, dayOffset)

            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            // Convert Calendar.SUNDAY=1..SATURDAY=7 to our 1=Mon..7=Sun
            val ourDayOfWeek = when (dayOfWeek) {
                Calendar.SUNDAY -> 7
                else -> dayOfWeek - 1
            }

            if (alarm.isDayEnabled(ourDayOfWeek)) {
                val triggerTime = candidate.timeInMillis
                if (triggerTime > now) {
                    // Check exception dates
                    if (!alarm.exceptionDates.contains(candidate.timeInMillis / 86400000 * 86400000)) {
                        return triggerTime
                    }
                }
            }
        }

        return null
    }

    private fun createPendingIntent(alarm: Alarm): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(EXTRA_ALARM_MINUTE, alarm.minute)
            putExtra(EXTRA_ALARM_RINGTONE, alarm.ringtoneUri)
            putExtra(EXTRA_ALARM_VOLUME, alarm.volume)
            putExtra(EXTRA_ALARM_VIBRATE, alarm.vibrate)
            putExtra(EXTRA_ALARM_SNOOZE_MINUTES, alarm.snoozeMinutes)
            putExtra(EXTRA_ALARM_GRADUAL_VOLUME, alarm.gradualVolume)
            putExtra(EXTRA_ALARM_CHALLENGE_TYPE, alarm.challengeType)
        }

        return PendingIntent.getBroadcast(
            context,
            generateRequestCode(alarm.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getShowAlarmPendingIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun getExactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            null
        }
    }

    companion object {
        fun generateRequestCode(alarmId: Long): Int {
            return REQUEST_CODE_BASE + alarmId.toInt()
        }
    }
}
