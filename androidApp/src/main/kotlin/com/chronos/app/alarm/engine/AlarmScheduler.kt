package com.chronos.app.alarm.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.chronos.app.alarm.receiver.AlarmTriggerReceiver
import com.chronos.app.data.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** AlarmManager adapter. The database remains the authority for alarm definitions. */
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
        const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"
        const val EXTRA_INSTANCE_ID = "extra_alarm_instance_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val ACTION_TRIGGER_ALARM = "com.chronos.action.TRIGGER_ALARM"

        fun generateRequestCode(alarmId: Long, salt: Int = 0): Int {
            return AlarmIdentity.requestCode(alarmId, salt)
        }

        fun instanceId(alarmId: Long, scheduledAt: Long, isSnooze: Boolean): String =
            AlarmIdentity.instance(alarmId, scheduledAt, isSnooze)
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Returns true when AlarmManager accepted the request (exact or inexact fallback). */
    fun scheduleAlarm(alarm: Alarm): Boolean {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return false
        }
        val triggerAt = calculateNextTriggerTime(alarm) ?: run {
            Log.w(TAG, "No future trigger time for alarm ${alarm.id}")
            cancelRegularAlarm(alarm.id)
            return false
        }
        val operation = createTriggerPendingIntent(alarm, triggerAt, isSnooze = false)

        return try {
            if (canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, getShowAlarmPendingIntent()),
                    operation
                )
            } else {
                Log.w(TAG, "Exact-alarm access is unavailable; scheduling an inexact fallback for ${alarm.id}")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            Log.i(TAG, "Scheduled alarm ${alarm.id} for ${Calendar.getInstance().apply { timeInMillis = triggerAt }.time}")
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Exact alarm access changed during scheduling; trying an inexact fallback", exception)
            scheduleInexact(triggerAt, operation, alarm.id)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Could not schedule alarm ${alarm.id}", exception)
            false
        }
    }

    /** A snooze has its own PendingIntent identity and cannot overwrite the recurring alarm. */
    fun scheduleSnooze(alarm: Alarm, triggerAt: Long): Boolean {
        if (triggerAt <= System.currentTimeMillis()) return false
        val operation = createTriggerPendingIntent(alarm, triggerAt, isSnooze = true)
        return try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                Log.w(TAG, "Exact-alarm access is unavailable; scheduling snooze as inexact")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Exact-alarm access changed during snooze scheduling; trying an inexact fallback", exception)
            scheduleInexact(triggerAt, operation, alarm.id)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Could not schedule snooze for alarm ${alarm.id}", exception)
            false
        }
    }

    private fun scheduleInexact(triggerAt: Long, operation: PendingIntent, alarmId: Long): Boolean = try {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        Log.i(TAG, "Scheduled inexact fallback for alarm $alarmId")
        true
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Could not schedule inexact fallback for alarm $alarmId", exception)
        false
    }

    fun cancelAlarm(alarmId: Long) {
        cancelRegularAlarm(alarmId)
        cancelSnooze(alarmId)
    }

    fun cancelRegularAlarm(alarmId: Long) = cancelOperation(alarmId, isSnooze = false)

    fun cancelSnooze(alarmId: Long) = cancelOperation(alarmId, isSnooze = true)

    fun rescheduleAllAlarms(alarms: List<Alarm>) {
        Log.i(TAG, "Reconciling ${alarms.size} enabled alarm definitions")
        alarms.forEach(::scheduleAlarm)
    }

    fun calculateNextTriggerTime(alarm: Alarm): Long? = AlarmTimeCalculator.calculateNextTriggerTime(alarm)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun getExactAlarmSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun cancelOperation(alarmId: Long, isSnooze: Boolean) {
        val pendingIntent = createTriggerPendingIntent(
            alarm = null,
            alarmId = alarmId,
            triggerAt = 0L,
            isSnooze = isSnooze,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createTriggerPendingIntent(
        alarm: Alarm,
        triggerAt: Long,
        isSnooze: Boolean
    ): PendingIntent = createTriggerPendingIntent(
        alarm = alarm,
        alarmId = alarm.id,
        triggerAt = triggerAt,
        isSnooze = isSnooze,
        flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )!!

    private fun createTriggerPendingIntent(
        alarm: Alarm?,
        alarmId: Long,
        triggerAt: Long,
        isSnooze: Boolean,
        flags: Int
    ): PendingIntent? {
        val kind = if (isSnooze) "snooze" else "regular"
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            data = Uri.parse("chronos://alarm/$alarmId/$kind")
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SCHEDULED_AT, triggerAt)
            putExtra(EXTRA_INSTANCE_ID, instanceId(alarmId, triggerAt, isSnooze))
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            alarm?.let {
                putExtra(EXTRA_ALARM_LABEL, it.label)
                putExtra(EXTRA_ALARM_HOUR, it.hour)
                putExtra(EXTRA_ALARM_MINUTE, it.minute)
                putExtra(EXTRA_ALARM_RINGTONE, it.ringtoneUri)
                putExtra(EXTRA_ALARM_VOLUME, it.volume)
                putExtra(EXTRA_ALARM_VIBRATE, it.vibrate)
                putExtra(EXTRA_ALARM_SNOOZE_MINUTES, it.snoozeMinutes)
                putExtra(EXTRA_ALARM_GRADUAL_VOLUME, it.gradualVolume)
                putExtra(EXTRA_ALARM_CHALLENGE_TYPE, it.challengeType)
            }
        }
        val requestCode = generateRequestCode(alarmId, if (isSnooze) 2 else 1)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun getShowAlarmPendingIntent(): PendingIntent {
        val intent = Intent(context, com.chronos.app.ui.MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
