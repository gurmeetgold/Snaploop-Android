package com.snaploop.app.data

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EventUpdatePayloadTest {
    @Test
    fun `details only payload carries optimistic version and omits date fields`() {
        val event = event()
        val expected = Instant.parse("2026-09-08T12:34:56Z")
        val payload = managedEventUpdatePayload(
            event = event,
            includeDates = false,
            expectedUpdatedAt = expected,
            now = Instant.parse("2026-09-08T13:00:00Z"),
        )

        assertEquals(expected.toEpochMilli(), payload["expectedUpdatedAtMillis"])
        assertEquals(event.id, payload["eventId"])
        assertEquals(event.name, payload["name"])
        assertFalse(payload.containsKey("startsAtMillis"))
        assertFalse(payload.containsKey("endsAtMillis"))
        assertFalse(payload.containsKey("startsAtOffsetMinutes"))
        assertFalse(payload.containsKey("endsAtOffsetMinutes"))
        assertFalse(payload.containsKey("nowOffsetMinutes"))
        assertFalse(payload.containsKey("photoWindowVersion"))
        assertFalse(payload.containsKey("photoWindowTimeZoneId"))
    }

    @Test
    fun `date payload includes canonical timing metadata`() {
        val event = event()
        val payload = managedEventUpdatePayload(
            event = event,
            includeDates = true,
            expectedUpdatedAt = event.updatedAt,
            now = Instant.parse("2026-09-08T13:00:00Z"),
        )

        assertTrue(payload.containsKey("startsAtMillis"))
        assertTrue(payload.containsKey("endsAtMillis"))
        assertTrue(payload.containsKey("startsAtOffsetMinutes"))
        assertTrue(payload.containsKey("endsAtOffsetMinutes"))
        assertTrue(payload.containsKey("nowOffsetMinutes"))
        assertEquals(event.photoWindowVersion, payload["photoWindowVersion"])
        assertEquals(event.photoWindowTimeZoneId, payload["photoWindowTimeZoneId"])
    }

    private fun event() = SnapEvent(
        id = "event",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = "organizer",
        name = "Event",
        category = EventCategory.trip,
        locationName = "Toronto",
        startsAt = Instant.parse("2026-09-08T04:00:00Z"),
        endsAt = Instant.parse("2026-09-11T03:59:59.999Z"),
        photoWindowVersion = SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION,
        photoWindowTimeZoneId = "America/Toronto",
        status = EventStatus.active,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-08T12:34:56Z"),
    )
}
