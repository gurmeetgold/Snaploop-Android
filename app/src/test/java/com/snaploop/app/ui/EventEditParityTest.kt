package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventEditParityTest {
    @Test
    fun `details only historical edit preserves every date field`() {
        val current = event()
        val plan = EventEditParityPolicy.plan(
            current = current,
            name = "Renamed Event",
            category = EventCategory.family,
            locationName = "Toronto",
            startsOn = LocalDate.of(2025, 1, 10),
            endsOn = LocalDate.of(2025, 1, 12),
        )

        assertFalse(plan.datesChanged)
        assertTrue(plan.hasChanges)
        assertEquals(current.startsAt, plan.event.startsAt)
        assertEquals(current.endsAt, plan.event.endsAt)
        assertEquals(current.photoWindowVersion, plan.event.photoWindowVersion)
        assertEquals(current.photoWindowTimeZoneId, plan.event.photoWindowTimeZoneId)
        assertEquals(current.photoWindowStartDayNumber, plan.event.photoWindowStartDayNumber)
        assertEquals(current.photoWindowEndDayNumber, plan.event.photoWindowEndDayNumber)
        assertEquals(current.updatedAt, plan.event.updatedAt)
        assertEquals("Renamed Event", plan.event.name)
        assertEquals(EventCategory.family, plan.event.category)
        assertEquals("Toronto", plan.event.locationName)
    }

    @Test
    fun `date edit canonicalizes in existing event timezone`() {
        val current = event()
        val plan = EventEditParityPolicy.plan(
            current = current,
            name = current.name,
            category = current.category,
            locationName = current.locationName,
            startsOn = LocalDate.of(2025, 2, 1),
            endsOn = LocalDate.of(2025, 2, 3),
        )

        assertTrue(plan.datesChanged)
        assertTrue(plan.hasChanges)
        assertEquals(Instant.parse("2025-02-01T05:00:00Z"), plan.event.startsAt)
        assertEquals(Instant.parse("2025-02-04T04:59:59.999Z"), plan.event.endsAt)
        assertEquals(SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION, plan.event.photoWindowVersion)
        assertEquals("America/Toronto", plan.event.photoWindowTimeZoneId)
        assertEquals(LocalDate.of(2025, 2, 1).toEpochDay().toInt(), plan.event.photoWindowStartDayNumber)
        assertEquals(LocalDate.of(2025, 2, 3).toEpochDay().toInt(), plan.event.photoWindowEndDayNumber)
    }

    @Test
    fun `no changes is detected after trimming`() {
        val current = event()
        val plan = EventEditParityPolicy.plan(
            current = current,
            name = "  ${current.name}  ",
            category = current.category,
            locationName = "  ${current.locationName}  ",
            startsOn = LocalDate.of(2025, 1, 10),
            endsOn = LocalDate.of(2025, 1, 12),
        )

        assertFalse(plan.datesChanged)
        assertFalse(plan.hasChanges)
    }

    private fun event() = SnapEvent(
        id = "event",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = "organizer",
        name = "Historical Event",
        category = EventCategory.trip,
        locationName = "Montreal",
        startsAt = Instant.parse("2025-01-10T05:00:00Z"),
        endsAt = Instant.parse("2025-01-13T04:59:59.999Z"),
        photoWindowVersion = SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION,
        photoWindowTimeZoneId = "America/Toronto",
        photoWindowStartDayNumber = LocalDate.of(2025, 1, 10).toEpochDay().toInt(),
        photoWindowEndDayNumber = LocalDate.of(2025, 1, 12).toEpochDay().toInt(),
        status = EventStatus.active,
        createdAt = Instant.parse("2024-12-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )
}
