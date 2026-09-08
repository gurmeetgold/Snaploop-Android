package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class EventDashboardParityTest {
    @Test
    fun `upcoming active event is paused and cannot sync`() {
        val event = event(
            startsAt = Instant.parse("2026-09-10T12:00:00Z"),
            endsAt = Instant.parse("2026-09-12T23:59:59Z"),
        )
        val now = Instant.parse("2026-09-08T12:00:00Z")

        assertEquals(EventDashboardParityPolicy.Lifecycle.UPCOMING, EventDashboardParityPolicy.lifecycle(event, now, 15))
        assertFalse(EventDashboardParityPolicy.canSync(event, now, 15))
    }

    @Test
    fun `live event can sync`() {
        val event = event(
            startsAt = Instant.parse("2026-09-08T00:00:00Z"),
            endsAt = Instant.parse("2026-09-10T23:59:59Z"),
        )
        val now = Instant.parse("2026-09-09T12:00:00Z")

        assertEquals(EventDashboardParityPolicy.Lifecycle.ACTIVE, EventDashboardParityPolicy.lifecycle(event, now, 15))
        assertTrue(EventDashboardParityPolicy.canSync(event, now, 15))
    }

    @Test
    fun `photo grace window can sync then expires`() {
        val event = event(
            startsAt = Instant.parse("2026-09-01T00:00:00Z"),
            endsAt = Instant.parse("2026-09-05T23:59:59Z"),
        )

        assertEquals(
            EventDashboardParityPolicy.Lifecycle.GRACE,
            EventDashboardParityPolicy.lifecycle(event, Instant.parse("2026-09-20T23:59:59Z"), 15),
        )
        assertTrue(EventDashboardParityPolicy.canSync(event, Instant.parse("2026-09-20T23:59:59Z"), 15))
        assertEquals(
            EventDashboardParityPolicy.Lifecycle.EXPIRED,
            EventDashboardParityPolicy.lifecycle(event, Instant.parse("2026-09-21T00:00:00Z"), 15),
        )
        assertFalse(EventDashboardParityPolicy.canSync(event, Instant.parse("2026-09-21T00:00:00Z"), 15))
    }

    @Test
    fun `grace addition follows event civil timezone across DST`() {
        val zone = ZoneId.of("America/Toronto")
        val end = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, zone).toInstant()
        val event = event(
            startsAt = ZonedDateTime.of(2026, 10, 30, 0, 0, 0, 0, zone).toInstant(),
            endsAt = end,
            timeZone = zone.id,
        )
        val expectedGraceEnd = end.atZone(zone).plusDays(2).toInstant()

        assertEquals(
            EventDashboardParityPolicy.Lifecycle.GRACE,
            EventDashboardParityPolicy.lifecycle(event, expectedGraceEnd, 2),
        )
        assertEquals(
            EventDashboardParityPolicy.Lifecycle.EXPIRED,
            EventDashboardParityPolicy.lifecycle(event, expectedGraceEnd.plusMillis(1), 2),
        )
    }

    @Test
    fun `non-active server statuses never sync`() {
        val now = Instant.parse("2026-09-09T12:00:00Z")
        listOf(
            EventStatus.endedByOrganizer,
            EventStatus.deletedByOrganizer,
            EventStatus.expired,
        ).forEach { status ->
            val event = event(
                startsAt = Instant.parse("2026-09-08T00:00:00Z"),
                endsAt = Instant.parse("2026-09-10T23:59:59Z"),
                status = status,
            )
            assertEquals(EventDashboardParityPolicy.Lifecycle.EXPIRED, EventDashboardParityPolicy.lifecycle(event, now, 15))
            assertFalse(EventDashboardParityPolicy.canSync(event, now, 15))
        }
    }

    @Test
    fun `sync display follows pinned iOS priority`() {
        assertEquals(
            EventDashboardParityPolicy.SyncDisplay.PAUSED,
            EventDashboardParityPolicy.syncDisplay(
                canSync = false,
                canReadPhotos = false,
                hasUser = true,
                sharingEnabled = true,
            ),
        )
        assertEquals(
            EventDashboardParityPolicy.SyncDisplay.NEEDS_PHOTO_ACCESS,
            EventDashboardParityPolicy.syncDisplay(
                canSync = true,
                canReadPhotos = false,
                hasUser = true,
                sharingEnabled = true,
            ),
        )
        assertEquals(
            EventDashboardParityPolicy.SyncDisplay.PAUSED,
            EventDashboardParityPolicy.syncDisplay(
                canSync = true,
                canReadPhotos = true,
                hasUser = false,
                sharingEnabled = true,
            ),
        )
        assertEquals(
            EventDashboardParityPolicy.SyncDisplay.AUTOMATIC,
            EventDashboardParityPolicy.syncDisplay(
                canSync = true,
                canReadPhotos = true,
                hasUser = true,
                sharingEnabled = true,
            ),
        )
        assertEquals(
            EventDashboardParityPolicy.SyncDisplay.SHARING_OFF,
            EventDashboardParityPolicy.syncDisplay(
                canSync = true,
                canReadPhotos = true,
                hasUser = true,
                sharingEnabled = false,
            ),
        )
    }

    @Test
    fun `dashboard count includes own matches only while sharing`() {
        val owners = listOf("me", "friend-a", "me", "friend-b")

        assertEquals(4, EventDashboardParityPolicy.photosOfMeCount(owners, "me", sharingEnabled = true))
        assertEquals(2, EventDashboardParityPolicy.photosOfMeCount(owners, "me", sharingEnabled = false))
    }

    private fun event(
        startsAt: Instant,
        endsAt: Instant,
        status: EventStatus = EventStatus.active,
        timeZone: String = "UTC",
    ) = SnapEvent(
        id = "event-1",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = "organizer",
        name = "Parity Event",
        category = EventCategory.trip,
        startsAt = startsAt,
        endsAt = endsAt,
        photoWindowVersion = SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION,
        photoWindowTimeZoneId = timeZone,
        status = status,
        createdAt = startsAt.minusSeconds(60),
        updatedAt = startsAt.minusSeconds(60),
    )
}
