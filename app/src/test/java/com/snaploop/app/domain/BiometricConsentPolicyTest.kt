package com.snaploop.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricConsentPolicyTest {
    private fun valid(country: String = "CA", subdivision: String = "ON", expiresAt: Long = 2_000L) = BiometricConsentRecord(
        userId = "u1",
        policyVersion = BiometricConsentRecord.CURRENT_POLICY_VERSION,
        disclosureId = BiometricConsentRecord.CURRENT_DISCLOSURE_ID,
        disclosureSha256 = BiometricConsentRecord.CURRENT_DISCLOSURE_SHA256,
        acceptedAtMillis = 1L,
        withdrawnAtMillis = null,
        expiredAtMillis = null,
        expiresAtMillis = expiresAt,
        jurisdictionCountry = country,
        jurisdictionSubdivision = subdivision,
        appVersion = "1.0",
        platform = "Android",
        locale = "en-CA",
        acceptedVia = BiometricConsentRecord.CONSENT_METHOD,
        age18Attested = true,
        noticeAcknowledged = true,
        ownFaceAttested = true,
        lastBiometricActivityAtMillis = null,
    )

    @Test fun canadaExceptQuebecAndIndiaAreSupported() {
        assertTrue(valid().isActive(nowMillis = 1_000L))
        assertFalse(valid(subdivision = "QC").isActive(nowMillis = 1_000L))
        assertTrue(valid(country = "IN", subdivision = "").isActive(nowMillis = 1_000L))
    }

    @Test fun expiryAndAttestationsFailClosed() {
        assertFalse(valid(expiresAt = 999L).isActive(nowMillis = 1_000L))
        assertFalse(valid().copy(ownFaceAttested = false).isActive(nowMillis = 1_000L))
        assertFalse(valid().copy(disclosureSha256 = "wrong").isActive(nowMillis = 1_000L))
    }
}
