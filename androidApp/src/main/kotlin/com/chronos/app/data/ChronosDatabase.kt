package com.chronos.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chronos.app.data.model.*

@Database(
    entities = [
        Alarm::class,
        CalendarEvent::class,
        Reminder::class,
        Timer::class,
        StopwatchLap::class,
        WorldClock::class,
        FocusSession::class,
        AlarmHistory::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ChronosDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchLapDao(): StopwatchLapDao
    abstract fun worldClockDao(): WorldClockDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: ChronosDatabase? = null

        fun getInstance(context: Context): ChronosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChronosDatabase::class.java,
                    "chronos_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
