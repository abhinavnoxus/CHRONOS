package com.chronos.app.alarm.engine

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chronos.app.data.model.Alarm
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmSchedulerTest {
    @Test fun exactPermissionDenialUsesInexactFallbackWithoutLosingAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(AlarmManager::class.java)
        shadowOf(manager).setCanScheduleExactAlarms(false)
        val scheduler = AlarmScheduler(context)
        val alarm = Alarm(id = 10, hour = 23, minute = 59)
        assertTrue(scheduler.scheduleAlarm(alarm))
        assertEquals(1, shadowOf(manager).getScheduledAlarms().size)
        scheduler.cancelAlarm(alarm.id)
        assertTrue(shadowOf(manager).getScheduledAlarms().isEmpty())
    }
}
