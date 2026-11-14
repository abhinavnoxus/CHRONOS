package com.chronos.app

import android.app.Application
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.di.ApplicationScope
import com.chronos.app.notifications.NotificationHelper
import com.chronos.app.reminders.ReminderRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChronosApplication : Application() {
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()
        applicationScope.launch {
            runCatching { runtimeController.reconcileOnAppStart(); reminderRepository.reconcile() }
                .onFailure { android.util.Log.e("ChronosApplication", "Alarm reconciliation failed", it) }
        }
    }
}
