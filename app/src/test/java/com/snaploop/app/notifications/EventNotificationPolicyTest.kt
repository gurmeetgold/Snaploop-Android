package com.snaploop.app.notifications

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EventNotificationPolicyTest {
    @Test
    fun malformedRecordsAreRejected() {
        assertNull(
            EventNotificationPolicy.fromRecord(
                documentId = "",
                eventId = "event-1",
                title = "Title",
                body = "Body",
                createdAt = Instant.EPOCH,
            ),
        )
        assertNull(EventNotificationPolicy.fromRecord("n1", null, "Title", "Body", Instant.EPOCH))
        assertNull(EventNotificationPolicy.fromRecord("n1", "event-1", "   ", "Body", Instant.EPOCH))
        assertNull(EventNotificationPolicy.fromRecord("n1", "event-1", "Title", "", Instant.EPOCH))
    }

    @Test
    fun missingTimestampFallsBackToDistantPastEquivalent() {
        val notification = EventNotificationPolicy.fromRecord(
            documentId = "n1",
            eventId = " event-1 ",
            title = "Title",
            body = "Body",
            createdAt = null,
        )

        assertEquals("event-1", notification?.eventId)
        assertEquals(Instant.MIN, notification?.createdAt)
        assertFalse(notification?.read ?: true)
    }

    @Test
    fun invitationRecordPreservesBackendNavigationMetadata() {
        val notification = EventNotificationPolicy.fromRecord(
            documentId = "invite_event-1_123",
            eventId = " event-1 ",
            title = "Event invitation",
            body = "You were invited to Summer Trip.",
            createdAt = Instant.parse("2026-09-09T08:00:00Z"),
            type = " event_invite ",
            eventName = " Summer Trip ",
            inviteToken = " invite-token-123 ",
            read = false,
        )

        assertEquals("event_invite", notification?.type)
        assertEquals("Summer Trip", notification?.eventName)
        assertEquals("invite-token-123", notification?.inviteToken)
        assertFalse(notification?.read ?: true)
    }

    @Test
    fun optionalBackendMetadataIsBackwardCompatible() {
        val notification = EventNotificationPolicy.fromRecord(
            documentId = "n1",
            eventId = "event-1",
            title = "Title",
            body = "Body",
            createdAt = Instant.EPOCH,
            type = "   ",
            eventName = "",
            inviteToken = null,
            read = null,
        )

        assertNull(notification?.type)
        assertNull(notification?.eventName)
        assertNull(notification?.inviteToken)
        assertFalse(notification?.read ?: true)
    }

    @Test
    fun unreadUpdatesAreSortedNewestFirst() {
        val older = EventNotification("old", "event-1", "Old", "Body", Instant.parse("2026-01-01T00:00:00Z"))
        val newer = EventNotification("new", "event-2", "New", "Body", Instant.parse("2026-02-01T00:00:00Z"))

        assertEquals(
            listOf("new", "old"),
            EventNotificationPolicy.newestFirst(listOf(older, newer)).map(EventNotification::id),
        )
    }

    @Test
    fun declinedEventEvictionPreservesOtherUpdates() {
        val first = EventNotification("n1", "event-1", "A", "Body", Instant.EPOCH)
        val sameEvent = EventNotification("n2", "event-1", "B", "Body", Instant.EPOCH)
        val other = EventNotification("n3", "event-2", "C", "Body", Instant.EPOCH)

        assertEquals(
            listOf("n3"),
            EventNotificationPolicy.withoutEvent(
                notifications = listOf(first, sameEvent, other),
                eventId = " event-1 ",
            ).map(EventNotification::id),
        )
    }
}
