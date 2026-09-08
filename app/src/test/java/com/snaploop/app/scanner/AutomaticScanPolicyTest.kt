package com.snaploop.app.scanner

import com.snaploop.app.media.PhotoAccessLevel
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticScanPolicyTest {
    private val now = Instant.parse("2026-09-08T18:00:00Z")

    @Test
    fun `live shared event with readable access is eligible`() {
        assertTrue(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
            ),
        )
    }

    @Test
    fun `selected photo access remains readable but explicit`() {
        assertTrue(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.SELECTED,
                powerSaveMode = false,
            ),
        )
    }

    @Test
    fun `future ended unshared denied and power save cases are rejected`() {
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(start = now.plusSeconds(60)),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
            ),
        )
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(status = EventStatus.endedByOrganizer),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
            ),
        )
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = false,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
            ),
        )
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.DENIED,
                powerSaveMode = false,
            ),
        )
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = true,
            ),
        )
    }

    @Test
    fun `one hour cooldown is enforced per event`() {
        val justRan = now.toEpochMilli() - AutomaticScanPolicy.COOLDOWN_MILLIS + 1L
        val cooledDown = now.toEpochMilli() - AutomaticScanPolicy.COOLDOWN_MILLIS

        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = justRan,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
            ),
        )
        assertTrue(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = cooledDown,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
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
        status = status,
        createdAt = now.minusSeconds(3600),
        updatedAt = now.minusSeconds(60),
    )
}
