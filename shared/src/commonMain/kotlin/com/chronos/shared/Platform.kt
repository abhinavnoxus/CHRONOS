package com.chronos.shared

/**
 * Platform-agnostic alarm scheduling interface.
 * Each platform provides its own implementation.
 */
interface AlarmSchedulerPlatform {
    fun scheduleAlarm(alarm: SharedAlarm)
    fun cancelAlarm(alarmId: Long)
    fun rescheduleAllAlarms(alarms: List<SharedAlarm>)
    fun canScheduleExactAlarms(): Boolean
}

/**
 * Platform-agnostic notification interface.
 */
interface NotificationPlatform {
    fun showAlarmNotification(alarm: SharedAlarm)
    fun dismissAlarmNotification(alarmId: Long)
    fun showReminderNotification(reminder: SharedReminder)
}
