package com.chronos.app.ui.screens.clock

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small durable monotonic snapshot; laps remain in Room. No background ticker is needed. */
@Singleton
class StopwatchStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("chronos_stopwatch", Context.MODE_PRIVATE)
    fun load(): StopwatchState {
        val running = prefs.getBoolean("running", false)
        val elapsed = prefs.getLong("elapsed", 0L)
        val savedAt = prefs.getLong("saved_elapsed", 0L)
        val accumulated = if (running) elapsed + (SystemClock.elapsedRealtime() - savedAt).coerceAtLeast(0L) else elapsed
        // elapsedRealtime resets on reboot. A running stopwatch becomes paused at its last snapshot.
        return StopwatchState(running = false, accumulated = accumulated, sessionId = prefs.getLong("session", 0L))
    }
    fun save(state: StopwatchState) {
        prefs.edit().putBoolean("running", state.running).putLong("elapsed", state.elapsed())
            .putLong("saved_elapsed", SystemClock.elapsedRealtime()).putLong("session", state.sessionId).apply()
    }
}
