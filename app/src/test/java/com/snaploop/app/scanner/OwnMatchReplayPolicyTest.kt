package com.snaploop.app.scanner

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnMatchReplayPolicyTest {
    private val now = Instant.parse("2026-09-08T18:00:00Z")

    @Test
    fun `authoritative false to true opt in while sharing during Event window replays`() {
        assertTrue(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = false,
                savedEnabled = true,
                sharingEnabled = true,
                event = event(),
                now = now,
                gracePeriodDays = 2,
            ),
        )
    }

    @Test
    fun `idempotent true save does not replay`() {
        assertFalse(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = true,
                savedEnabled = true,
                sharingEnabled = true,
                event = event(),
                now = now,
            ),
        )
    }

    @Test
    fun `disabled saved preference or sharing never replays`() {
        assertFalse(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = false,
                savedEnabled = false,
                sharingEnabled = true,
                event = event(),
                now = now,
            ),
        )
        assertFalse(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = false,
                savedEnabled = true,
                sharingEnabled = false,
                event = event(),
                now = now,
            ),
        )
    }

    @Test
    fun `active Event remains replay eligible during configured grace`() {
        val recentlyEnded = event(
            start = now.minusSeconds(3L * 24L * 60L * 60L),
            end = now.minusSeconds(60L * 60L),
        )
        assertTrue(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = false,
                savedEnabled = true,
                sharingEnabled = true,
                event = recentlyEnded,
                now = now,
                gracePeriodDays = 2,
            ),
        )
        assertFalse(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = false,
                savedEnabled = true,
                sharingEnabled = true,
                event = recentlyEnded,
                now = now,
                gracePeriodDays = 0,
            ),
        )
    }

    @Test
    fun `organizer-ended Event never replays`() {
        assertFalse(
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = false,
                savedEnabled = true,
                sharingEnabled = true,
                event = event(status = EventStatus.endedByOrganizer),
                now = now,
                gracePeriodDays = 2,
            ),
        )
    }

    private fun event(
        start: Instant = now.minusSeconds(60),
        end: Instant = now.plusSeconds(60),
        status: EventStatus = EventStatus.active,
    ) = SnapEvent(
        id = "event-1",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = "owner",
        name = "Trip",
        category = EventCategory.trip,
        startsAt = start,
        endsAt = end,
        photoWindowTimeZoneId = "UTC",
        status = status,
        createdAt = now.minusSeconds(3600),
        updatedAt = now.minusSeconds(60),
    )
}
