package com.snaploop.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InvitationActionPolicyTest {
    @Test
    fun reviewRemainsReview() {
        assertEquals(
            InvitationActionPolicy.Dispatch.REVIEW,
            InvitationActionPolicy.dispatch(InviteAction.REVIEW),
        )
    }

    @Test
    fun acceptRemainsAccept() {
        assertEquals(
            InvitationActionPolicy.Dispatch.ACCEPT,
            InvitationActionPolicy.dispatch(InviteAction.ACCEPT),
        )
    }

    @Test
    fun declineNeverFallsBackToReview() {
        assertEquals(
            InvitationActionPolicy.Dispatch.DECLINE,
            InvitationActionPolicy.dispatch(InviteAction.DECLINE),
        )
    }
}
