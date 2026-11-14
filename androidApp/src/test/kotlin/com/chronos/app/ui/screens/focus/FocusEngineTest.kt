package com.chronos.app.ui.screens.focus

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.settings.ChronosSettings
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
class FocusEngineTest {
    private class TestClock(var wall: Long = 1_000_000L, var elapsed: Long = 500L, var boot: Int = 1) : FocusClock {
        override fun wallMillis() = wall
        override fun elapsedMillis() = elapsed
        override fun bootCount() = boot
    }
    private lateinit var db: ChronosDatabase
    private lateinit var settings: ChronosSettings
    private lateinit var clock: TestClock
    private lateinit var engine: FocusEngine

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java).allowMainThreadQueries().build()
        settings = ChronosSettings(context)
        clock = TestClock()
        engine = FocusEngine(settings, db.focusSessionDao(), clock)
        kotlinx.coroutines.runBlocking { settings.clearFocusRuntime() }
    }
    @After fun tearDown() { db.close() }

    @Test fun pauseAndResumePreserveRemainingTime() = runTest {
        engine.start("Writing", 25, 5, 15, 4)
        clock.elapsed += 60_000L; clock.wall += 60_000L
        engine.pause()
        val paused = settings.focusRuntime.first()
        assertEquals(24 * 60_000L, engine.remaining(paused, clock.elapsed, clock.boot))
        clock.elapsed += 120_000L; clock.wall += 120_000L
        assertEquals(24 * 60_000L, engine.remaining(paused, clock.elapsed, clock.boot))
        engine.resume()
        assertEquals(24 * 60_000L, engine.remaining(settings.focusRuntime.first(), clock.elapsed, clock.boot))
    }

    @Test fun backgroundCatchUpAdvancesAllElapsedPhasesWithoutDuplication() = runTest {
        engine.start("Work", 5, 1, 5, 2)
        clock.elapsed += 13 * 60_000L; clock.wall += 13 * 60_000L
        engine.reconcile(clock.wall, clock.elapsed, clock.boot)
        val once = settings.focusRuntime.first()
        assertEquals(2, once.completedFocusSessions)
        assertEquals("long_break", once.phase)
        assertEquals(10 * 60_000L, once.totalFocusMillis)
        engine.reconcile(clock.wall, clock.elapsed, clock.boot)
        assertEquals(once, settings.focusRuntime.first())
    }

    @Test fun rebootFallsBackToWallDeadline() = runTest {
        engine.start("Work", 5, 1, 5, 2)
        clock.boot = 2; clock.elapsed = 10L; clock.wall += 13 * 60_000L
        engine.reconcile(clock.wall, clock.elapsed, clock.boot)
        val runtime = settings.focusRuntime.first()
        assertEquals(2, runtime.completedFocusSessions)
        assertEquals("long_break", runtime.phase)
    }

    @Test fun stopRecordsPartialFocusOnce() = runTest {
        engine.start("Work", 25, 5, 15, 4)
        clock.elapsed += 10 * 60_000L; clock.wall += 10 * 60_000L
        engine.stop(); engine.stop()
        val sessions = db.focusSessionDao().getAllSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(10, sessions.first().totalFocusMinutes)
        assertNotNull(sessions.first().endedAt)
        assertFalse(settings.focusRuntime.first().isActive)
    }
    @Test fun stoppingFocusKeepsConfiguredDefaultRounds() = runTest {
        settings.setFocusDefaults(25, 5, 15, 6)
        engine.start("Work", 25, 5, 15, 2)
        engine.stop()
        assertEquals(6, settings.preferences.first().sessionsBeforeLongBreak)
    }
}
