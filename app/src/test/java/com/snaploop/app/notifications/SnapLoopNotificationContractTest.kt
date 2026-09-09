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
        val route = SnapLoopNotificationContract.route(
            mapOf("inviteToken" to token, "eventId" to "event-123"),
        )

        assertEquals(SnapLoopNotificationContract.Route.Invite(token), route)
    }

    @Test
    fun invalidInviteFallsThroughToValidEvent() {
        val route = SnapLoopNotificationContract.route(
            mapOf("inviteToken" to "invalid", "eventId" to " event-123 "),
        )

        assertEquals(SnapLoopNotificationContract.Route.EventPhotos("event-123"), route)
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
    fun visibleCopyUsesPrivacySafeFallbacksAndBoundsRemoteText() {
        assertEquals(
            SnapLoopNotificationContract.VisibleCopy(
                title = "SnapLoop invitation",
                body = "You have a new Event invitation.",
            ),
            SnapLoopNotificationContract.visibleCopy(
                route = SnapLoopNotificationContract.Route.Invite(token),
                remoteTitle = null,
                remoteBody = null,
            ),
        )

        val bounded = SnapLoopNotificationContract.visibleCopy(
            route = null,
            remoteTitle = "  Hello  ",
            remoteBody = "x".repeat(250),
        )
        assertEquals("Hello", bounded.title)
        assertEquals(180, bounded.body.length)
    }

    @Test
    fun notificationIdsAreStableAndRouteScoped() {
        val invite = SnapLoopNotificationContract.Route.Invite(token)
        val event = SnapLoopNotificationContract.Route.EventPhotos("event-123")

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
