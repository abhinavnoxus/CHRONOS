package com.chronos.app.alarm.engine

import org.junit.Assert.*
import org.junit.Test

class AlarmIdentityTest {
    @Test fun regularAndSnoozeAreDifferentInstances() {
        assertNotEquals(AlarmIdentity.instance(1, 1000, false), AlarmIdentity.instance(1, 1000, true))
    }
    @Test fun futureOccurrenceHasDifferentIdentity() {
        assertNotEquals(AlarmIdentity.instance(1, 1000, false), AlarmIdentity.instance(1, 2000, false))
    }
    @Test fun alteredIntentPayloadIsRejected() {
        val id = AlarmIdentity.instance(7, 123456, false)
        assertTrue(AlarmIdentity.matches(7, id, 123456, false))
        assertFalse(AlarmIdentity.matches(8, id, 123456, false))
        assertFalse(AlarmIdentity.matches(7, id, 123457, false))
        assertFalse(AlarmIdentity.matches(7, id, 123456, true))
        assertFalse(AlarmIdentity.matches(-1, id, 123456, false))
        assertFalse(AlarmIdentity.matches(7, id, 0, false))
    }
    @Test fun notificationIdentityIsStableAcrossProcessCalls() {
        val id = AlarmIdentity.instance(42, 1700000000000, true)
        assertEquals(AlarmIdentity.notificationId(id), AlarmIdentity.notificationId(id))
        assertTrue(AlarmIdentity.notificationId(id) > 0)
    }
    @Test fun notificationHashCollisionDoesNotOverwriteLiveAlarm() {
        val first = AlarmIdentity.notificationId("first")
        assertEquals(first + 1, AlarmIdentity.availableNotificationId("second", setOf(first)))
        assertEquals(1, AlarmIdentity.availableNotificationId("third", setOf(Int.MAX_VALUE)))
    }
}
