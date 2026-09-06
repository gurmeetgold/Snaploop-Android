package com.snaploop.app.model

import java.time.Instant

data class BiometricJurisdiction(val countryCode: String, val subdivisionCode: String = "") {
    val normalizedCountry = countryCode.uppercase()
    val normalizedSubdivision = subdivisionCode.uppercase()
    val isFaceMatchAvailable: Boolean
        get() = when (normalizedCountry) {
            "IN" -> normalizedSubdivision.isEmpty()
            "CA" -> normalizedSubdivision in CANADIAN_SUBDIVISIONS
            else -> false
        }
    companion object {
        val CANADIAN_SUBDIVISIONS = setOf("AB","BC","MB","NB","NL","NS","NT","NU","ON","PE","SK","YT")
    }
}

data class BiometricConsentRecord(
    val userId: String,
    val policyVersion: Int = CURRENT_POLICY_VERSION,
    val disclosureId: String = CURRENT_DISCLOSURE_ID,
    val disclosureSha256: String = CURRENT_DISCLOSURE_SHA256,
    val acceptedAt: Instant,
    val withdrawnAt: Instant? = null,
    val expiredAt: Instant? = null,
    val expiresAt: Instant? = null,
    val jurisdictionCountry: String,
    val jurisdictionSubdivision: String = "",
    val appVersion: String,
    val platform: String = "Android",
    val locale: String,
    val acceptedVia: String = CONSENT_METHOD,
    val age18Attested: Boolean,
    val noticeAcknowledged: Boolean,
    val ownFaceAttested: Boolean,
    val lastBiometricActivityAt: Instant? = null
) {
    val isActive: Boolean
        get() = policyVersion == CURRENT_POLICY_VERSION && disclosureId == CURRENT_DISCLOSURE_ID &&
            disclosureSha256 == CURRENT_DISCLOSURE_SHA256 && withdrawnAt == null && expiredAt == null &&
            expiresAt?.isAfter(Instant.now()) == true && age18Attested && noticeAcknowledged && ownFaceAttested &&
            BiometricJurisdiction(jurisdictionCountry, jurisdictionSubdivision).isFaceMatchAvailable

    companion object {
        const val CURRENT_POLICY_VERSION = 5
        const val CURRENT_DISCLOSURE_ID = "biometric-consent-v5"
        const val CURRENT_DISCLOSURE_SHA256 = "2b78a5de4ced7219953cf4c3b62e07dce41392b0090f7c07c3fcb307411bc30f"
        const val CONSENT_METHOD = "explicit-button"
    }
}
