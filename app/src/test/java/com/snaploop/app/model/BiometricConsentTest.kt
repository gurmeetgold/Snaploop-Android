package com.snaploop.app.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BiometricConsentTest {
    @Test fun canadaJurisdictionParity() {
        assertTrue(BiometricJurisdiction("CA", "ON").isFaceMatchAvailable)
        assertFalse(BiometricJurisdiction("CA", "QC").isFaceMatchAvailable)
        assertTrue(BiometricJurisdiction("IN").isFaceMatchAvailable)
        assertFalse(BiometricJurisdiction("US", "IL").isFaceMatchAvailable)
    }

    @Test fun consentRequiresAllV5AttestationsAndExpiry() {
        val record = BiometricConsentRecord(
            userId="u", acceptedAt=Instant.now(), expiresAt=Instant.now().plusSeconds(3600),
            jurisdictionCountry="CA", jurisdictionSubdivision="ON", appVersion="1.0.0", locale="en_CA",
            age18Attested=true, noticeAcknowledged=true, ownFaceAttested=true
        )
        assertTrue(record.isActive)
        assertFalse(record.copy(ownFaceAttested=false).isActive)
        assertFalse(record.copy(jurisdictionSubdivision="QC").isActive)
    }
}
