package com.snaploop.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteNotificationCleanupPolicyTest {
    private val token = "AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun matchingEventAndValidInviteTokenAreCancelled() {
        assertTrue(
            InviteNotificationCleanupPolicy.shouldCancel(
                targetEventId = "event-123",
                payloadEventId = " event-123 ",
                inviteToken = token,
            ),
        )
    }

    @Test
    fun differentEventIsPreserved() {
        assertFalse(
            InviteNotificationCleanupPolicy.shouldCancel(
                targetEventId = "event-123",
                payloadEventId = "event-456",
                inviteToken = token,
            ),
        )
    }

    @Test
    fun photoUpdateWithoutInviteTokenIsPreserved() {
        assertFalse(
            InviteNotificationCleanupPolicy.shouldCancel(
                targetEventId = "event-123",
                payloadEventId = "event-123",
                inviteToken = null,
            ),
        )
    }

    @Test
    fun malformedValuesAreNeverCancelled() {
        assertFalse(InviteNotificationCleanupPolicy.shouldCancel("", "event-123", token))
        assertFalse(InviteNotificationCleanupPolicy.shouldCancel("event-123", "event/123", token))
        assertFalse(InviteNotificationCleanupPolicy.shouldCancel("event-123", "event-123", "bad"))
    }
}
