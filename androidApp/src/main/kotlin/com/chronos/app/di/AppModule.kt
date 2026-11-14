package com.chronos.app.di

import android.content.Context
import com.chronos.app.data.AlarmDao
import com.chronos.app.data.AlarmHistoryDao
import com.chronos.app.data.CalendarEventDao
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.FocusSessionDao
import com.chronos.app.data.ReminderDao
import com.chronos.app.data.StopwatchLapDao
import com.chronos.app.data.TimerDao
import com.chronos.app.data.WorldClockDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChronosDatabase {
        return ChronosDatabase.getInstance(context)
    }

    @Provides fun provideAlarmDao(db: ChronosDatabase): AlarmDao = db.alarmDao()
    @Provides fun provideCalendarEventDao(db: ChronosDatabase): CalendarEventDao = db.calendarEventDao()
    @Provides fun provideReminderDao(db: ChronosDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideTimerDao(db: ChronosDatabase): TimerDao = db.timerDao()
    @Provides fun provideStopwatchLapDao(db: ChronosDatabase): StopwatchLapDao = db.stopwatchLapDao()
    @Provides fun provideWorldClockDao(db: ChronosDatabase): WorldClockDao = db.worldClockDao()
    @Provides fun provideFocusSessionDao(db: ChronosDatabase): FocusSessionDao = db.focusSessionDao()
    @Provides fun provideAlarmHistoryDao(db: ChronosDatabase): AlarmHistoryDao = db.alarmHistoryDao()

}
