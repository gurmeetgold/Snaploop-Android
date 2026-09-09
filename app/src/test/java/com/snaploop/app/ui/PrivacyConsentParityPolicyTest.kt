package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConsentParityPolicyTest {
    @Test
    fun `active consent is reviewable and withdrawable`() {
        val presentation = PrivacyConsentParityPolicy.presentation(true)

        assertEquals("Active · Review or withdraw", presentation.statusText)
        assertTrue(presentation.canWithdraw)
    }

    @Test
    fun `inactive consent is never presented as active`() {
        val presentation = PrivacyConsentParityPolicy.presentation(false)

        assertEquals("Not active · Review details", presentation.statusText)
        assertFalse(presentation.canWithdraw)
    }

    @Test
    fun `unknown consent remains non destructive while loading`() {
        val presentation = PrivacyConsentParityPolicy.presentation(null)

        assertEquals("Checking Face Match consent…", presentation.statusText)
        assertFalse(presentation.canWithdraw)
    }
}
