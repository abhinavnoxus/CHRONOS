package com.chronos.app.alarm.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronos.app.data.ChronosDatabase
import com.chronos.app.data.model.Alarm
import com.chronos.app.data.model.AlarmInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room + real durable runtime mirror; no mocked DAOs hiding transaction/idempotency bugs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmRuntimeControllerTest {
    private lateinit var context: Context
    private lateinit var db: ChronosDatabase
    private lateinit var store: AlarmRuntimeStore
    private lateinit var controller: AlarmRuntimeController
    private val scheduled = 1_800_000_000_000L
    private val instance = AlarmIdentity.instance(1, scheduled, false)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("chronos_alarm_runtime", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java).allowMainThreadQueries().build()
        store = AlarmRuntimeStore(context)
        controller = AlarmRuntimeController(context, db, AlarmScheduler(context), store)
    }
    @After fun tearDown() { db.close() }

    private suspend fun seed(repeat: Boolean = false): Alarm {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, repeatDays = if (repeat) Alarm.EVERY_DAY else 0)
        db.alarmDao().insertAlarm(alarm)
        return alarm
    }
    private suspend fun activate(isSnooze: Boolean = false, at: Long = scheduled): String {
        val id = AlarmIdentity.instance(1, at, isSnooze)
        store.activate(1, id, at, isSnooze)
        assertTrue(controller.activate(1, id, at, isSnooze))
        return id
    }

    @Test fun dismissWithoutLiveServiceSingletonStillUpdatesAuthoritativeRow() = runTest {
        seed(); activate()
        // Simulate process recreation: no AlarmPlaybackService.instance exists in this test process.
        assertTrue(controller.dismiss(1, instance, scheduled, false))
        assertEquals(AlarmInstance.DISMISSED, db.alarmInstanceDao().getById(instance)?.state)
        assertFalse(store.isActive(1, instance))
        assertFalse(db.alarmDao().getAlarmById(1)!!.isEnabled)
    }

    @Test fun repeatedDismissIsIdempotentAndDoesNotDuplicateHistory() = runTest {
        seed(); activate()
        assertTrue(controller.dismiss(1, instance, scheduled, false))
        assertFalse(controller.dismiss(1, instance, scheduled, false))
        assertFalse(controller.dismiss(1, instance, scheduled, false))
        assertEquals(1, db.alarmHistoryDao().getRecentHistory().firstValue().count { it.action == "dismissed" })
    }

    @Test fun earlyTapWritesTombstoneAndBlocksPendingActivation() = runTest {
        seed()
        store.activate(1, instance, scheduled, false)
        assertTrue(controller.dismiss(1, instance, scheduled, false))
        store.activate(1, instance, scheduled, false) // Redelivered start must not resurrect it.
        assertFalse(controller.activate(1, instance, scheduled, false))
        assertEquals(AlarmInstance.DISMISSED, db.alarmInstanceDao().getById(instance)?.state)
    }

    @Test fun staleUnknownIntentCannotFabricateDismissal() = runTest {
        seed()
        val stale = AlarmIdentity.instance(1, scheduled - 86_400_000L, false)
        assertFalse(controller.dismiss(1, stale, scheduled - 86_400_000L, false))
        assertNull(db.alarmInstanceDao().getById(stale))
        assertTrue(db.alarmHistoryDao().getRecentHistory().firstValue().isEmpty())
    }

    @Test fun mismatchedAlarmIdCannotDismissAnotherRow() = runTest {
        seed(); activate()
        db.alarmDao().insertAlarm(Alarm(id = 2, hour = 8, minute = 0))
        val other = AlarmIdentity.instance(2, scheduled, false)
        store.activate(2, other, scheduled, false); assertTrue(controller.activate(2, other, scheduled, false))
        assertFalse(controller.dismiss(2, instance, scheduled, false))
        assertEquals(AlarmInstance.RINGING, db.alarmInstanceDao().getById(instance)?.state)
        assertTrue(controller.dismiss(1, instance, scheduled, false))
        assertEquals(AlarmInstance.RINGING, db.alarmInstanceDao().getById(other)?.state)
    }

    @Test fun multipleSimultaneousAlarmsRemainIndependent() = runTest {
        seed(); activate()
        db.alarmDao().insertAlarm(Alarm(id = 2, hour = 7, minute = 0))
        val other = AlarmIdentity.instance(2, scheduled, false)
        store.activate(2, other, scheduled, false)
        assertTrue(controller.activate(2, other, scheduled, false))
        assertTrue(controller.dismiss(1, instance, scheduled, false))
        assertTrue(store.isActive(2, other))
        assertEquals(AlarmInstance.RINGING, db.alarmInstanceDao().getById(other)?.state)
        assertTrue(db.alarmDao().getAlarmById(2)!!.isRinging)
    }

    @Test fun repeatingAlarmKeepsFutureEnabledAfterDismissal() = runTest {
        seed(repeat = true); activate()
        assertTrue(controller.dismiss(1, instance, scheduled, false))
        assertTrue(db.alarmDao().getAlarmById(1)!!.isEnabled)
        assertFalse(db.alarmDao().getAlarmById(1)!!.isRinging)
    }

    @Test fun snoozePreservesRegularDefinitionAndCompletesOnSnoozeActivation() = runTest {
        seed(repeat = true); activate()
        assertTrue(controller.snooze(1, instance, scheduled, false))
        val original = db.alarmInstanceDao().getById(instance)!!
        assertEquals(AlarmInstance.SNOOZED, original.state)
        assertNotNull(original.snoozedUntil)
        assertTrue(db.alarmDao().getAlarmById(1)!!.isEnabled)
        assertFalse(store.isActive(1, instance))
        val due = original.snoozedUntil!!
        val snoozeId = activate(isSnooze = true, at = due)
        assertEquals(AlarmInstance.COMPLETED, db.alarmInstanceDao().getById(instance)?.state)
        assertTrue(controller.dismiss(1, snoozeId, due, true))
        assertTrue(db.alarmDao().getAlarmById(1)!!.isEnabled)
    }

    @Test fun alteredSnoozePayloadIsRejected() = runTest {
        seed(); activate()
        assertFalse(controller.snooze(1, instance, scheduled + 1, false))
        assertEquals(AlarmInstance.RINGING, db.alarmInstanceDao().getById(instance)?.state)
    }

    @Test fun bootInterruptsRingingAndRecordsMissedWithoutDisablingRepeats() = runTest {
        seed(repeat = true); activate()
        controller.recoverAfterBoot()
        assertEquals(AlarmInstance.INTERRUPTED, db.alarmInstanceDao().getById(instance)?.state)
        assertFalse(store.isActive(1, instance))
        assertTrue(db.alarmDao().getAlarmById(1)!!.isEnabled)
        assertEquals(1, db.alarmHistoryDao().getRecentHistory().firstValue().count { it.action == "missed" })
    }

    @Test fun tamperedInstanceIdIsIgnored() = runTest {
        seed(); activate()
        assertFalse(controller.dismiss(1, "forged", scheduled, false))
        assertEquals(AlarmInstance.RINGING, db.alarmInstanceDao().getById(instance)?.state)
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = this.first()
}
