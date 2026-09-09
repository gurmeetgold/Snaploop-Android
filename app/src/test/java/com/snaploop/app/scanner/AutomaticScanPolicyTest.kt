package com.snaploop.app.scanner

import com.snaploop.app.domain.EventParticipant
import com.snaploop.app.domain.FaceTemplatePose
import com.snaploop.app.domain.FaceTemplateRecord
import com.snaploop.app.media.PhotoAccessLevel
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun `active event remains eligible during configured grace window`() {
        val recentlyEnded = event(
            start = now.minusSeconds(3L * 24L * 60L * 60L),
            end = now.minusSeconds(60L * 60L),
        )
        assertTrue(
            AutomaticScanPolicy.shouldRun(
                event = recentlyEnded,
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
                gracePeriodDays = 2,
            ),
        )
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = recentlyEnded,
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
                gracePeriodDays = 0,
            ),
        )
    }

    @Test
    fun `active event is rejected after configured grace window`() {
        assertFalse(
            AutomaticScanPolicy.shouldRun(
                event = event(
                    start = now.minusSeconds(5L * 24L * 60L * 60L),
                    end = now.minusSeconds(3L * 24L * 60L * 60L),
                ),
                now = now,
                lastAutomaticScanAtMillis = null,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
                gracePeriodDays = 2,
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

    @Test
    fun `changed preference generation bypasses cooldown`() {
        val justRan = now.toEpochMilli() - 1_000L
        assertTrue(
            AutomaticScanPolicy.shouldRun(
                event = event(),
                now = now,
                lastAutomaticScanAtMillis = justRan,
                sharingEnabled = true,
                photoAccess = PhotoAccessLevel.FULL,
                powerSaveMode = false,
                triggerChanged = true,
            ),
        )
    }

    @Test
    fun `trigger fingerprint changes with own match preference revision`() {
        val event = event()
        val before = AutomaticScanPolicy.triggerFingerprint(event, "share=id:a;own=id:off")
        val after = AutomaticScanPolicy.triggerFingerprint(event, "share=id:a;own=id:on")
        assertNotEquals(before, after)
        assertEquals(before, AutomaticScanPolicy.triggerFingerprint(event, "share=id:a;own=id:off"))
    }

    @Test
    fun `roster aware fingerprint is stable regardless of participant order`() {
        val first = participant("user-a", "member-a", "face-a", "template-a")
        val second = participant("user-b", "member-b", "face-b", "template-b")
        val before = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(first, second),
            "source-membership",
            "share=on;own=off",
        )
        val reordered = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(second, first),
            "source-membership",
            "share=on;own=off",
        )
        assertEquals(before, reordered)
    }

    @Test
    fun `roster membership face and template generations bypass cooldown fingerprint`() {
        val baseline = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(participant("user-a", "member-a", "face-a", "template-a")),
            "source-membership-a",
            "share=on;own=off",
        )
        val rejoined = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(participant("user-a", "member-b", "face-a", "template-a")),
            "source-membership-a",
            "share=on;own=off",
        )
        val replacedIdentity = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(participant("user-a", "member-a", "face-b", "template-a")),
            "source-membership-a",
            "share=on;own=off",
        )
        val refreshedTemplate = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(participant("user-a", "member-a", "face-a", "template-b")),
            "source-membership-a",
            "share=on;own=off",
        )
        val sourceRejoined = AutomaticScanPolicy.triggerFingerprint(
            event(),
            listOf(participant("user-a", "member-a", "face-a", "template-a")),
            "source-membership-b",
            "share=on;own=off",
        )

        assertNotEquals(baseline, rejoined)
        assertNotEquals(baseline, replacedIdentity)
        assertNotEquals(baseline, refreshedTemplate)
        assertNotEquals(baseline, sourceRejoined)
    }

    @Test
    fun `automatic sync persistence keys are account isolated without storing uid`() {
        val first = AutomaticSyncIdentityScope.storageKey("last.", "installation-a", "event-1")
        val second = AutomaticSyncIdentityScope.storageKey("last.", "installation-b", "event-1")

        assertEquals("last.installation-a.event-1", first)
        assertNotEquals(first, second)
        assertNull(AutomaticSyncIdentityScope.storageKey("last.", "   ", "event-1"))
    }

    @Test
    fun `legacy participant epoch changes on leave and rejoin`() {
        val first = participant("user-a", null, "face-a", "template-a", joinedAtMillis = 1_000L)
        val rejoined = participant("user-a", null, "face-a", "template-a", joinedAtMillis = 2_000L)

        assertNotEquals(
            AutomaticSyncIdentityScope.participantEpoch(first),
            AutomaticSyncIdentityScope.participantEpoch(rejoined),
        )
    }

    private fun participant(
        userId: String,
        membershipId: String?,
        faceIdentityId: String,
        templateId: String,
        joinedAtMillis: Long = 1_000L,
    ) = EventParticipant(
        userId = userId,
        membershipId = membershipId,
        displayName = null,
        faceIdentityId = faceIdentityId,
        faceEmbedding = floatArrayOf(1f),
        faceTemplates = listOf(
            FaceTemplateRecord(
                id = templateId,
                embedding = floatArrayOf(1f),
                pose = FaceTemplatePose.CENTER,
                quality = 1.0,
                createdAtMillis = joinedAtMillis,
            ),
        ),
        faceProfileVersion = 5,
        joinedAtMillis = joinedAtMillis,
    )

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
