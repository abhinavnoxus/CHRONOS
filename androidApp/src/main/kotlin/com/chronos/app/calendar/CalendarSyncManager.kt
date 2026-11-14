package com.chronos.app.calendar

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.chronos.app.data.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Calendar Integration
 *
 * Reads calendar events from the device's calendar provider.
 * Read-only access — CHRONOS suggests alarms but never creates
 * calendar events without explicit user approval.
 */
@Singleton
class CalendarSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CalendarSyncManager"
        private const val SYNC_WINDOW_HOURS = 48
    }

    /**
     * Fetch today's and upcoming events from all calendars.
     */
    suspend fun fetchUpcomingEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()

        try {
            val now = System.currentTimeMillis()
            val windowEnd = now + (SYNC_WINDOW_HOURS * 3600_000L)

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

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0"
            val selectionArgs = arrayOf(now.toString(), windowEnd.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val calIdIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIdx = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val colorIdx = it.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val hasAlarmIdx = it.getColumnIndex(CalendarContract.Events.HAS_ALARM)
                val rruleIdx = it.getColumnIndex(CalendarContract.Events.RRULE)

                while (it.moveToNext()) {
                    val event = CalendarEvent(
                        eventId = it.getLong(idIdx),
                        calendarId = it.getLong(calIdIdx),
                        title = it.getString(titleIdx) ?: "Untitled",
                        description = it.getString(descIdx) ?: "",
                        location = it.getString(locIdx) ?: "",
                        startTime = it.getLong(startIdx),
                        endTime = it.getLong(endIdx),
                        allDay = it.getInt(allDayIdx) == 1,
                        color = if (it.isNull(colorIdx)) 0xFFD4A853.toInt() else it.getInt(colorIdx),
                        hasAlarm = it.getInt(hasAlarmIdx) == 1,
                        isRecurring = !it.getString(rruleIdx).isNullOrEmpty()
                    )
                    events.add(event)
                }
            }

            Log.i(TAG, "Fetched ${events.size} calendar events")
        } catch (e: SecurityException) {
            Log.w(TAG, "Calendar permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching calendar events", e)
        }

        events
    }

    /**
     * Detect overlapping events.
     */
    fun detectConflicts(events: List<CalendarEvent>): List<Pair<CalendarEvent, CalendarEvent>> {
        val conflicts = mutableListOf<Pair<CalendarEvent, CalendarEvent>>()
        val sorted = events.sortedBy { it.startTime }

        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (sorted[j].startTime < sorted[i].endTime) {
                    conflicts.add(sorted[i] to sorted[j])
                } else {
                    break
                }
            }
        }

        return conflicts
    }

    /**
     * Find gaps between events (free time blocks).
     */
    fun findGaps(events: List<CalendarEvent>, minGapMinutes: Int = 30): List<Pair<Long, Long>> {
        val gaps = mutableListOf<Pair<Long, Long>>()
        val sorted = events.sortedBy { it.startTime }

        for (i in 0 until sorted.size - 1) {
            val gapStart = sorted[i].endTime
            val gapEnd = sorted[i + 1].startTime
            val gapMinutes = (gapEnd - gapStart) / 60_000

            if (gapMinutes >= minGapMinutes) {
                gaps.add(gapStart to gapEnd)
            }
        }

        return gaps
    }
}
