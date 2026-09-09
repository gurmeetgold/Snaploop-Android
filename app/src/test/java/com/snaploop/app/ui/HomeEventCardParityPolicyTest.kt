package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HomeEventCardParityPolicyTest {
    @Test
    fun `active lifecycle labels match pinned iOS HomeView`() {
        val event = event(
            startsAt = Instant.parse("2026-09-10T00:00:00Z"),
            endsAt = Instant.parse("2026-09-12T23:59:59Z"),
        )

        assertEquals(
            HomeEventCardParityPolicy.Presentation("UPCOMING", HomeEventCardParityPolicy.Tint.BLUE),
            HomeEventCardParityPolicy.presentation(event, Instant.parse("2026-09-09T12:00:00Z"), 15),
        )
        assertEquals(
            HomeEventCardParityPolicy.Presentation("LIVE", HomeEventCardParityPolicy.Tint.MINT),
            HomeEventCardParityPolicy.presentation(event, Instant.parse("2026-09-11T12:00:00Z"), 15),
        )
        assertEquals(
            HomeEventCardParityPolicy.Presentation("WRAPPING UP", HomeEventCardParityPolicy.Tint.AMBER),
            HomeEventCardParityPolicy.presentation(event, Instant.parse("2026-09-20T12:00:00Z"), 15),
        )
        assertEquals(
            HomeEventCardParityPolicy.Presentation("COMPLETED", HomeEventCardParityPolicy.Tint.SECONDARY),
            HomeEventCardParityPolicy.presentation(event, Instant.parse("2026-09-28T00:00:00Z"), 15),
        )
    }

    @Test
    fun `server terminal statuses override lifecycle`() {
        val now = Instant.parse("2026-09-11T12:00:00Z")
        assertEquals(
            "ENDED",
            HomeEventCardParityPolicy.presentation(event(status = EventStatus.endedByOrganizer), now, 15).text,
        )
        assertEquals(
            "DELETED",
            HomeEventCardParityPolicy.presentation(event(status = EventStatus.deletedByOrganizer), now, 15).text,
        )
        assertEquals(
            "COMPLETED",
            HomeEventCardParityPolicy.presentation(event(status = EventStatus.expired), now, 15).text,
        )
    }

    private fun event(
        startsAt: Instant = Instant.parse("2026-09-10T00:00:00Z"),
        endsAt: Instant = Instant.parse("2026-09-12T23:59:59Z"),
        status: EventStatus = EventStatus.active,
    ) = SnapEvent(
        id = "home-event",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = "organizer",
        name = "Home Event",
        category = EventCategory.trip,
        startsAt = startsAt,
        endsAt = endsAt,
        photoWindowVersion = SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION,
        photoWindowTimeZoneId = "UTC",
        status = status,
        createdAt = startsAt.minusSeconds(60),
        updatedAt = startsAt.minusSeconds(60),
    )
}
