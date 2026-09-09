package com.snaploop.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSetupDeletionCopyTest {
    @Test
    fun `face setup deletion copy accurately preserves consent contract`() {
        val copy = FaceSetupDeletionCopy.CONFIRMATION_BODY
        assertTrue(copy.contains("biometric consent remains active"))
        assertTrue(copy.contains("withdraw it separately in Privacy & Data"))
        assertFalse(copy.contains("consent and set it up again"))
    }
}
