package com.snaploop.app.ui

import com.snaploop.app.core.InviteAction
import com.snaploop.app.model.EventMember
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class InvitationReviewParityPolicyTest {
    @Test
    fun `preview inviter name wins over roster fallback`() {
        assertEquals(
            "Admin Sender",
            InvitationReviewParityPolicy.inviterLabel(
                previewName = "  Admin Sender  ",
                members = listOf(member("organizer", "Organizer Name")),
                organizerUserId = "organizer",
            ),
        )
    }

    @Test
    fun `organizer roster name is used when invite preview has no name`() {
        assertEquals(
            "Organizer Name",
            InvitationReviewParityPolicy.inviterLabel(
                previewName = "   ",
                members = listOf(member("organizer", "  Organizer Name  ")),
                organizerUserId = "organizer",
            ),
        )
    }

    @Test
    fun `automatic action copy matches pinned iOS`() {
        assertEquals("Joining Event…", InvitationReviewParityPolicy.automaticProgressCopy(InviteAction.ACCEPT))
        assertEquals("Declining invitation…", InvitationReviewParityPolicy.automaticProgressCopy(InviteAction.DECLINE))
        assertEquals("Opening invitation…", InvitationReviewParityPolicy.automaticProgressCopy(InviteAction.REVIEW))
    }

    private fun member(userId: String, displayName: String?) = EventMember(
        userId = userId,
        displayName = displayName,
        role = EventMember.Role.organizer,
        joinedAt = Instant.parse("2026-09-01T00:00:00Z"),
        sharingEnabled = true,
        faceTemplateVersion = 5,
    )
}
