package com.snaploop.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreAuthOnboardingParityTest {
    @Test
    fun `global completion wins`() {
        assertTrue(
            PreAuthOnboardingParity.hasCompleted(
                globalCompleted = true,
                legacyEntries = emptyMap<String, Any?>(),
            ),
        )
    }

    @Test
    fun `legacy completed account migrates to device level semantics`() {
        assertTrue(
            PreAuthOnboardingParity.hasCompleted(
                globalCompleted = false,
                legacyEntries = mapOf<String, Any?>(
                    "onboarding.user-123.v1" to true,
                    "other" to false,
                ),
            ),
        )
    }

    @Test
    fun `unrelated preferences do not skip onboarding`() {
        assertFalse(
            PreAuthOnboardingParity.hasCompleted(
                globalCompleted = false,
                legacyEntries = mapOf<String, Any?>(
                    "face_setup.skipped.user-123.v1" to true,
                    "onboarding.user-123.v2" to true,
                ),
            ),
        )
    }
}
