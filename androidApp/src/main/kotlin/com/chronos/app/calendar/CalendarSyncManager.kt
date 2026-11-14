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
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.HAS_ALARM,
            CalendarContract.Events.RRULE
        )
        // Include events already in progress, not only events whose start is still in the future.
        val selection = "${CalendarContract.Events.DTEND} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0"
        val arguments = arrayOf(dayStart.toString(), windowEnd.toString())
        val events = mutableListOf<CalendarEvent>()

        val cursor: Cursor? = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arguments,
            "${CalendarContract.Events.DTSTART} ASC"
        )
        cursor?.use { result ->
            val id = result.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val calendarId = result.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
            val title = result.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val description = result.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val location = result.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val start = result.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val end = result.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val allDay = result.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val color = result.getColumnIndexOrThrow(CalendarContract.Events.EVENT_COLOR)
            val hasAlarm = result.getColumnIndexOrThrow(CalendarContract.Events.HAS_ALARM)
            val rule = result.getColumnIndexOrThrow(CalendarContract.Events.RRULE)

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
                    hasAlarm = result.getInt(hasAlarm) != 0,
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
