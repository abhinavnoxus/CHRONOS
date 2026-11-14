package com.chronos.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chronos.app.data.model.*

@Database(
    entities = [
        Alarm::class,
        AlarmInstance::class,
        CalendarEvent::class,
        Reminder::class,
        Timer::class,
        StopwatchLap::class,
        WorldClock::class,
        FocusSession::class,
        AlarmHistory::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ChronosDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmInstanceDao(): AlarmInstanceDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchLapDao(): StopwatchLapDao
    abstract fun worldClockDao(): WorldClockDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `alarm_instances` (
                        `instanceId` TEXT NOT NULL,
                        `alarmId` INTEGER NOT NULL,
                        `scheduledTimestamp` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `triggeredAt` INTEGER,
                        `dismissedAt` INTEGER,
                        `snoozedUntil` INTEGER,
                        `isSnooze` INTEGER NOT NULL,
                        PRIMARY KEY(`instanceId`)
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alarm_instances_alarmId_state` " +
                        "ON `alarm_instances` (`alarmId`, `state`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alarm_instances_scheduledTimestamp` " +
                        "ON `alarm_instances` (`scheduledTimestamp`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: ChronosDatabase? = null

        fun getInstance(context: Context): ChronosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChronosDatabase::class.java,
                    "chronos_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Never fall back to a destructive migration: local alarm data is user data.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
