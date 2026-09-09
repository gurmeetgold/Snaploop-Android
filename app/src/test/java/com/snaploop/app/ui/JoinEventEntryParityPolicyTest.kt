package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinEventEntryParityPolicyTest {
    @Test
    fun `continue is enabled only for nonblank manual entry`() {
        assertFalse(JoinEventEntryParityPolicy.canContinue("   "))
        assertTrue(JoinEventEntryParityPolicy.canContinue("ABC234"))
    }

    @Test
    fun `valid event code resolves without local error`() {
        assertNull(JoinEventEntryParityPolicy.validationError("ABC234"))
    }

    @Test
    fun `invalid input uses pinned iOS error copy`() {
        assertEquals(
            "That QR code, Event code, or invite link isn't valid.",
            JoinEventEntryParityPolicy.validationError("not a valid invite"),
        )
    }
}
