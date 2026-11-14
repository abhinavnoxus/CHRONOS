package com.chronos.app

import android.app.Application
import com.chronos.app.alarm.engine.AlarmRuntimeController
import com.chronos.app.di.ApplicationScope
import com.chronos.app.notifications.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChronosApplication : Application() {
    @Inject lateinit var runtimeController: AlarmRuntimeController
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()
        applicationScope.launch {
            runCatching { runtimeController.reconcileOnAppStart() }
                .onFailure { android.util.Log.e("ChronosApplication", "Alarm reconciliation failed", it) }
        }
    }
}
