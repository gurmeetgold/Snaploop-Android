package com.snaploop.app.ui

import com.snaploop.app.model.BiometricJurisdiction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConsentDetailPolicyTest {
    @Test
    fun `accept requires available jurisdiction and both attestations`() {
        val ontario = BiometricJurisdiction("CA", "ON")
        assertFalse(PrivacyConsentDetailPolicy.canAccept(ontario, ageConfirmed = false, noticeConfirmed = true, saving = false))
        assertFalse(PrivacyConsentDetailPolicy.canAccept(ontario, ageConfirmed = true, noticeConfirmed = false, saving = false))
        assertFalse(PrivacyConsentDetailPolicy.canAccept(ontario, ageConfirmed = true, noticeConfirmed = true, saving = true))
        assertTrue(PrivacyConsentDetailPolicy.canAccept(ontario, ageConfirmed = true, noticeConfirmed = true, saving = false))
    }

    @Test
    fun `quebec remains unavailable for Face Match`() {
        assertFalse(
            PrivacyConsentDetailPolicy.canAccept(
                BiometricJurisdiction("CA", "QC"),
                ageConfirmed = true,
                noticeConfirmed = true,
                saving = false,
            )
        )
    }

    @Test
    fun `residence attestation mirrors pinned iOS wording`() {
        assertEquals(
            "I confirm I am at least 18 years old and ordinarily reside in Ontario, Canada.",
            PrivacyConsentDetailPolicy.residenceAttestation(BiometricJurisdiction("CA", "ON")),
        )
        assertEquals(
            "I confirm I am at least 18 years old and ordinarily reside in India.",
            PrivacyConsentDetailPolicy.residenceAttestation(BiometricJurisdiction("IN")),
        )
    }
}
