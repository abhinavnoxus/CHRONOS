package com.chronos.app.calendar

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.chronos.app.data.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only Android Calendar Provider adapter. Events are never edited by CHRONOS. */
@Singleton
class CalendarSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun fetchUpcomingEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val windowEnd = dayStart + DAYS_TO_SYNC * 24L * 60L * 60L * 1_000L
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.RRULE
        )
        // Instances expands recurrences and uses occurrence boundaries, including in-progress events.
        val events = mutableListOf<CalendarEvent>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(dayStart.toString()).appendPath(windowEnd.toString()).build()
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
        cursor?.use { result ->
            val id = result.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val calendarId = result.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val title = result.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val description = result.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val location = result.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val start = result.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val end = result.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDay = result.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val color = result.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_COLOR)
            val rule = result.getColumnIndexOrThrow(CalendarContract.Instances.RRULE)

            while (result.moveToNext()) {
                events += CalendarEvent(
                    eventId = result.getLong(id),
                    calendarId = result.getLong(calendarId),
                    title = result.getString(title) ?: "Untitled event",
                    description = result.getString(description).orEmpty(),
                    location = result.getString(location).orEmpty(),
                    startTime = result.getLong(start),
                    endTime = result.getLong(end),
                    allDay = result.getInt(allDay) != 0,
                    color = if (result.isNull(color)) 0xFFC6A36B.toInt() else result.getInt(color),
                    hasAlarm = false,
                    isRecurring = !result.getString(rule).isNullOrBlank()
                )
            }
        }
        Log.i(TAG, "Loaded ${events.size} calendar event records")
        events
    }

    fun detectConflicts(events: List<CalendarEvent>): List<Pair<CalendarEvent, CalendarEvent>> {
        val conflicts = mutableListOf<Pair<CalendarEvent, CalendarEvent>>()
        val sorted = events.sortedBy { it.startTime }
        for (index in sorted.indices) {
            for (next in index + 1 until sorted.size) {
                if (sorted[next].startTime < sorted[index].endTime) conflicts += sorted[index] to sorted[next]
                else break
            }
        }
        return conflicts
    }

    fun findGaps(events: List<CalendarEvent>, minGapMinutes: Int = 30): List<Pair<Long, Long>> {
        val sorted = events.sortedBy { it.endTime }
        return sorted.zipWithNext().mapNotNull { (current, next) ->
            val gap = next.startTime - current.endTime
            if (gap >= minGapMinutes * 60_000L) current.endTime to next.startTime else null
        }
    }

    companion object {
        private const val TAG = "CalendarSyncManager"
        private const val DAYS_TO_SYNC = 7
    }
}
