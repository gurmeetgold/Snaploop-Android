package com.snaploop.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapLoopNotificationContractTest {
    private val token = "AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun validInviteHasPrecedenceOverEvent() {
        val payload = SnapLoopNotificationContract.parse(
            mapOf("inviteToken" to token, "eventId" to "event-123"),
        )

        assertEquals(SnapLoopNotificationKind.INVITE, payload.kind)
        assertEquals(token, payload.inviteToken)
        assertEquals("event-123", payload.eventId)
    }

    @Test
    fun invalidInviteFallsThroughToValidEvent() {
        val payload = SnapLoopNotificationContract.parse(
            mapOf("inviteToken" to "invalid", "eventId" to " event-123 "),
        )

        assertEquals(SnapLoopNotificationKind.EVENT_PHOTOS, payload.kind)
        assertNull(payload.inviteToken)
        assertEquals("event-123", payload.eventId)
    }

    @Test
    fun invalidEventIdsAreRejected() {
        assertNull(SnapLoopNotificationContract.normalizeEventId(""))
        assertNull(SnapLoopNotificationContract.normalizeEventId("event/123"))
        assertNull(SnapLoopNotificationContract.normalizeEventId("event\\123"))
        assertNull(SnapLoopNotificationContract.normalizeEventId("event\n123"))
        assertNull(SnapLoopNotificationContract.normalizeEventId("x".repeat(129)))
    }

    @Test
    fun copyUsesPrivacySafeFallbacksAndBoundsRemoteText() {
        val fallback = SnapLoopNotificationContract.parse(mapOf("inviteToken" to token))
        assertEquals("SnapLoop", fallback.title)
        assertEquals("You have a new SnapLoop Event invitation.", fallback.body)

        val bounded = SnapLoopNotificationContract.parse(
            data = mapOf("eventId" to "event-123"),
            notificationTitle = "  Hello  ",
            notificationBody = "x".repeat(300),
        )
        assertEquals("Hello", bounded.title)
        assertEquals(240, bounded.body.length)
    }

    @Test
    fun notificationIdsAreStableAndKindScoped() {
        val invite = SnapLoopNotificationContract.parse(mapOf("inviteToken" to token))
        val event = SnapLoopNotificationContract.parse(mapOf("eventId" to "event-123"))

        assertEquals(
            SnapLoopNotificationContract.notificationId(invite),
            SnapLoopNotificationContract.notificationId(invite),
        )
        assertNotEquals(
            SnapLoopNotificationContract.notificationId(invite),
            SnapLoopNotificationContract.notificationId(event),
        )
        assertEquals(
            SnapLoopNotificationContract.notificationId(invite),
            SnapLoopNotificationContract.inviteNotificationId(token),
        )
        assertNull(SnapLoopNotificationContract.inviteNotificationId("invalid"))
        assertTrue(SnapLoopNotificationContract.notificationId(event) >= 0)
    }
}
