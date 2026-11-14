package com.chronos.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
class ChronosMigrationTest {
    private lateinit var context: Context
    private val name = "migration-v2-v3-test"
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        // Build only the pre-v4 alarm/reminder subset needed to prove additive migration preserves rows.
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { onConfigure(db); createV2(db) }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, old: Int, new: Int) {}
            }).build())
        createV2(helper.writableDatabase)
        helper.writableDatabase.execSQL("INSERT INTO alarms (id,hour,minute,label,isEnabled,isRinging,repeatDays,exceptionDates,oneTimeDate,ringtoneUri,ringtoneTitle,vibrate,volume,snoozeEnabled,snoozeMinutes,snoozeCount,gradualVolume,createdAt,updatedAt) VALUES (7,7,30,'Keep me',1,0,0,'[]',NULL,NULL,NULL,1,0.8,1,5,0,1,100,200)")
        helper.writableDatabase.execSQL("INSERT INTO reminders (id,title,description,scheduledTime,isCompleted,completedAt,isRecurring,repeatInterval,createdAt) VALUES (9,'Keep reminder','',1234,0,NULL,0,0,500)")
        helper.close()
    }
    @After fun teardown() { context.deleteDatabase(name) }

    @Test fun migration2To3PreservesExistingRowsAndAddsRoutines() = runTest {
        val db = Room.databaseBuilder(context, ChronosDatabase::class.java, name).addMigrations(ChronosDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        assertEquals("Keep me", db.alarmDao().getAlarmById(7)?.label)
        assertEquals("Keep reminder", db.reminderDao().getReminderById(9)?.title)
        val id = db.routineDao().insert(Routine(title = "New routine", hour = 9, minute = 0))
        assertEquals("New routine", db.routineDao().observeAll().first().single { it.id == id }.title)
        assertEquals(3, db.openHelper.writableDatabase.version)
        db.close()
    }

    private fun createV2(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, label TEXT NOT NULL, isEnabled INTEGER NOT NULL, isRinging INTEGER NOT NULL, repeatDays INTEGER NOT NULL, exceptionDates TEXT NOT NULL, oneTimeDate INTEGER, ringtoneUri TEXT, ringtoneTitle TEXT, vibrate INTEGER NOT NULL, volume REAL NOT NULL, snoozeEnabled INTEGER NOT NULL, snoozeMinutes INTEGER NOT NULL, snoozeCount INTEGER NOT NULL, gradualVolume INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, scheduledTime INTEGER NOT NULL, isCompleted INTEGER NOT NULL, completedAt INTEGER, isRecurring INTEGER NOT NULL, repeatInterval INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
    }
}
