package com.snaploop.app.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationResumeStoreTest {
    @After
    fun tearDown() {
        InvitationResumeStore.clear()
    }

    @Test
    fun `review token capture preserves decline eligibility`() {
        val token = "AbCdEfGhJkLmNpQrStUvWx"

        InvitationResumeStore.capture(
            JoinIntent(token = token, action = InviteAction.REVIEW),
        )

        val state = InvitationResumeStore.state.value
        assertEquals(token, state?.token)
        assertEquals(InviteAction.REVIEW, state?.action)
        assertTrue(state?.canDecline == true)
        assertFalse(state?.awaitingFaceSetup == true)
    }

    @Test
    fun `fresh capture is not mistaken for process restored work`() {
        InvitationResumeStore.capture(
            JoinIntent(token = "AbCdEfGhJkLmNpQrStUvWx", action = InviteAction.REVIEW),
        )

        assertNull(InvitationResumeStore.takeRestoredForResolution())
    }

    @Test
    fun `retry arm is consumed exactly once`() {
        val token = "AbCdEfGhJkLmNpQrStUvWx"
        InvitationResumeStore.capture(
            JoinIntent(token = token, action = InviteAction.ACCEPT),
        )
        InvitationResumeStore.rearmRestoredForResolution()

        assertEquals(token, InvitationResumeStore.takeRestoredForResolution()?.token)
        assertNull(InvitationResumeStore.takeRestoredForResolution())
    }

    @Test
    fun `accept token survives face setup without becoming review`() {
        val token = "AbCdEfGhJkLmNpQrStUvWx"

        InvitationResumeStore.capture(
            JoinIntent(token = token, action = InviteAction.ACCEPT),
        )
        InvitationResumeStore.beginFaceSetup(
            fallbackToken = token,
            action = InviteAction.ACCEPT,
        )

        val awaiting = InvitationResumeStore.state.value
        assertEquals(token, awaiting?.token)
        assertEquals(InviteAction.ACCEPT, awaiting?.action)
        assertFalse(awaiting?.canDecline == true)
        assertTrue(awaiting?.awaitingFaceSetup == true)

        InvitationResumeStore.markFaceSetupResumed()
        assertFalse(InvitationResumeStore.state.value?.awaitingFaceSetup == true)
        assertEquals(InviteAction.ACCEPT, InvitationResumeStore.state.value?.action)
    }

    @Test
    fun `code review survives face setup and does not gain token decline semantics`() {
        val code = "AB2KM9"
        val fallbackToken = "AbCdEfGhJkLmNpQrStUvWx"

        InvitationResumeStore.capture(
            JoinIntent(code = code, action = InviteAction.REVIEW),
        )
        InvitationResumeStore.beginFaceSetup(
            fallbackToken = fallbackToken,
        )

        val state = InvitationResumeStore.state.value
        assertEquals(code, state?.code)
        assertEquals(fallbackToken, state?.token)
        assertEquals(InviteAction.REVIEW, state?.action)
        assertFalse(state?.canDecline == true)
        assertTrue(state?.awaitingFaceSetup == true)
    }

    @Test
    fun `clear removes routing context`() {
        InvitationResumeStore.capture(
            JoinIntent(token = "AbCdEfGhJkLmNpQrStUvWx", action = InviteAction.REVIEW),
        )

        InvitationResumeStore.clear()

        assertNull(InvitationResumeStore.state.value)
    }
}
