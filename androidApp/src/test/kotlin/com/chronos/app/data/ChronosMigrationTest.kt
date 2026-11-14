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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reconstructs the v2 table set from the unchanged v3 entities; not a historic schema fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChronosMigrationTest {
    private lateinit var context: Context
    private val name = "migration-v2-v3-test"
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(name) }
    @After fun teardown() { context.deleteDatabase(name) }

    @Test fun migration2To3PreservesExistingRowsAndAddsRoutines() = runTest {
        val before = Room.databaseBuilder(context, ChronosDatabase::class.java, name).allowMainThreadQueries().build()
        before.alarmDao().insertAlarm(Alarm(id = 7, hour = 7, minute = 30, label = "Keep me"))
        before.reminderDao().insertReminder(Reminder(id = 9, title = "Keep reminder", scheduledTime = 1234))
        val raw = before.openHelper.writableDatabase
        raw.execSQL("DROP TABLE routines")
        raw.version = 2
        before.close()
        val after = Room.databaseBuilder(context, ChronosDatabase::class.java, name)
            .addMigrations(ChronosDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        assertEquals("Keep me", after.alarmDao().getAlarmById(7)?.label)
        assertEquals("Keep reminder", after.reminderDao().getReminderById(9)?.title)
        val id = after.routineDao().insert(Routine(title = "New routine", hour = 9, minute = 0))
        assertEquals("New routine", after.routineDao().observeAll().first().single { it.id == id }.title)
        assertEquals(3, after.openHelper.writableDatabase.version)
        after.close()
    }
}
