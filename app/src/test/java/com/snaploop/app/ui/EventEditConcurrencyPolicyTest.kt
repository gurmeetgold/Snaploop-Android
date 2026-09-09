package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEditConcurrencyPolicyTest {
    @Test
    fun `same revision is fresh`() {
        val opened = event("2026-09-09T20:00:00Z")
        assertTrue(EventEditConcurrencyPolicy.isFresh(opened, opened.copy()))
    }

    @Test
    fun `newer server revision is stale`() {
        val opened = event("2026-09-09T20:00:00Z")
        val latest = opened.copy(updatedAt = Instant.parse("2026-09-09T20:05:00Z"))
        assertFalse(EventEditConcurrencyPolicy.isFresh(opened, latest))
    }

    @Test
    fun `different event can never be treated as same baseline`() {
        val opened = event("2026-09-09T20:00:00Z")
        assertFalse(EventEditConcurrencyPolicy.isFresh(opened, opened.copy(id = "other")))
    }

    private fun event(updatedAt: String) = SnapEvent(
        id = "event",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = "organizer",
        name = "Trip",
        category = EventCategory.trip,
        startsAt = Instant.parse("2026-09-10T04:00:00Z"),
        endsAt = Instant.parse("2026-09-13T03:59:59.999Z"),
        status = EventStatus.active,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse(updatedAt),
    )
}
