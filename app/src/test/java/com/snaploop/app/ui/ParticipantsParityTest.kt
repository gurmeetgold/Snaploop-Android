package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ParticipantsParityTest {
    @Test
    fun `organizer can manage admins and members but not self or organizer`() {
        val organizer = member("organizer", EventMember.Role.organizer)
        val admin = member("admin", EventMember.Role.admin)
        val participant = member("member", EventMember.Role.participant)

        assertFalse(ParticipantsParityPolicy.canManage(EventMember.Role.organizer, "organizer", organizer))
        assertTrue(ParticipantsParityPolicy.canManage(EventMember.Role.organizer, "organizer", admin))
        assertTrue(ParticipantsParityPolicy.canManage(EventMember.Role.organizer, "organizer", participant))
    }

    @Test
    fun `admin can remove only ordinary members`() {
        val admin = member("other-admin", EventMember.Role.admin)
        val participant = member("member", EventMember.Role.participant)
        val organizer = member("organizer", EventMember.Role.organizer)

        assertFalse(ParticipantsParityPolicy.canManage(EventMember.Role.admin, "me", admin))
        assertTrue(ParticipantsParityPolicy.canManage(EventMember.Role.admin, "me", participant))
        assertFalse(ParticipantsParityPolicy.canManage(EventMember.Role.admin, "me", organizer))
    }

    @Test
    fun `only organizer can change member roles`() {
        val admin = member("admin", EventMember.Role.admin)
        val participant = member("member", EventMember.Role.participant)

        assertTrue(ParticipantsParityPolicy.canChangeRole(EventMember.Role.organizer, "organizer", admin))
        assertTrue(ParticipantsParityPolicy.canChangeRole(EventMember.Role.organizer, "organizer", participant))
        assertFalse(ParticipantsParityPolicy.canChangeRole(EventMember.Role.admin, "me", participant))
        assertFalse(ParticipantsParityPolicy.canChangeRole(EventMember.Role.participant, "me", participant))
    }

    @Test
    fun `self management is always blocked`() {
        val self = member("me", EventMember.Role.participant)
        assertFalse(ParticipantsParityPolicy.canManage(EventMember.Role.organizer, "me", self))
        assertFalse(ParticipantsParityPolicy.canChangeRole(EventMember.Role.organizer, "me", self))
    }

    @Test
    fun `invite and leave rules match pinned iOS`() {
        assertTrue(ParticipantsParityPolicy.canInvite(EventMember.Role.organizer))
        assertTrue(ParticipantsParityPolicy.canInvite(EventMember.Role.admin))
        assertFalse(ParticipantsParityPolicy.canInvite(EventMember.Role.participant))

        assertFalse(ParticipantsParityPolicy.canLeave(EventMember.Role.organizer))
        assertTrue(ParticipantsParityPolicy.canLeave(EventMember.Role.admin))
        assertTrue(ParticipantsParityPolicy.canLeave(EventMember.Role.participant))
        assertTrue(ParticipantsParityPolicy.canLeave(null))
    }

    @Test
    fun `event creator is organizer even if membership snapshot disagrees`() {
        val event = event(creator = "me")
        val members = listOf(member("me", EventMember.Role.participant))
        assertTrue(ParticipantsParityPolicy.currentRole(event, members, "me") == EventMember.Role.organizer)
    }

    private fun member(id: String, role: EventMember.Role) = EventMember(
        userId = id,
        displayName = id,
        role = role,
        joinedAt = Instant.parse("2026-09-01T00:00:00Z"),
        sharingEnabled = true,
        faceTemplateVersion = 0,
    )

    private fun event(creator: String) = SnapEvent(
        id = "event",
        joinCode = "ABC123",
        inviteToken = "token",
        creatorUserId = creator,
        name = "Event",
        category = EventCategory.trip,
        startsAt = Instant.parse("2026-09-01T00:00:00Z"),
        endsAt = Instant.parse("2026-09-10T23:59:59Z"),
        status = EventStatus.active,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )
}
