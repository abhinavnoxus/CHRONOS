package com.chronos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronos.app.data.model.Alarm
import com.chronos.app.data.model.Reminder
import com.chronos.app.data.model.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChronosDatabaseTest {
    private lateinit var db: ChronosDatabase
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }

    @Test fun alarmCrudAndEnabledFilterUseRealRoom() = runTest {
        val id = db.alarmDao().insertAlarm(Alarm(hour = 6, minute = 45, label = "Morning"))
        assertEquals("Morning", db.alarmDao().getAlarmById(id)?.label)
        db.alarmDao().setAlarmEnabled(id, false)
        assertTrue(db.alarmDao().getEnabledAlarms().first().isEmpty())
        db.alarmDao().deleteAlarmById(id)
        assertNull(db.alarmDao().getAlarmById(id))
    }

    @Test fun reminderCompletionIsPersisted() = runTest {
        val id = db.reminderDao().insertReminder(Reminder(title = "Review", scheduledTime = 1234L))
        db.reminderDao().markCompleted(id, 5678L)
        val saved = db.reminderDao().getReminderById(id)!!
        assertTrue(saved.isCompleted)
        assertEquals(5678L, saved.completedAt)
        assertTrue(db.reminderDao().getActiveReminders().first().isEmpty())
    }

    @Test fun routineCrudPreservesOtherData() = runTest {
        val alarmId = db.alarmDao().insertAlarm(Alarm(hour = 7, minute = 0))
        val id = db.routineDao().insert(Routine(title = "Shutdown", hour = 18, minute = 0, repeatDays = 31))
        assertEquals(1, db.routineDao().observeAll().first().size)
        db.routineDao().delete(id)
        assertTrue(db.routineDao().observeAll().first().isEmpty())
        assertNotNull(db.alarmDao().getAlarmById(alarmId))
    }

    @Test fun insertIfAbsentDoesNotOverwriteDismissedState() = runTest {
        val instance = com.chronos.app.data.model.AlarmInstance("1:alarm:100", 1, 100, "dismissed")
        db.alarmInstanceDao().insertIfAbsent(instance)
        db.alarmInstanceDao().insertIfAbsent(instance.copy(state = "ringing"))
        assertEquals("dismissed", db.alarmInstanceDao().getById(instance.instanceId)?.state)
    }
}
