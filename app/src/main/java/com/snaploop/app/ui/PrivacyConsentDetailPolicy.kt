package com.snaploop.app.ui

import com.snaploop.app.model.BiometricJurisdiction

/** Pure interaction policy mirrored from pinned iOS BiometricConsentView. */
internal object PrivacyConsentDetailPolicy {
    fun canAccept(
        jurisdiction: BiometricJurisdiction,
        ageConfirmed: Boolean,
        noticeConfirmed: Boolean,
        saving: Boolean,
    ): Boolean = jurisdiction.isFaceMatchAvailable && ageConfirmed && noticeConfirmed && !saving

    fun residenceAttestation(jurisdiction: BiometricJurisdiction): String = when {
        jurisdiction.normalizedCountry == "IN" ->
            "I confirm I am at least 18 years old and ordinarily reside in India."
        jurisdiction.normalizedCountry == "CA" && jurisdiction.normalizedSubdivision.isNotBlank() ->
            "I confirm I am at least 18 years old and ordinarily reside in ${canadianSubdivisionName(jurisdiction.normalizedSubdivision)}, Canada."
        else -> "Face Match is not available for this region."
    }

    fun canadianSubdivisionName(code: String): String = when (code.uppercase()) {
        "AB" -> "Alberta"
        "BC" -> "British Columbia"
        "MB" -> "Manitoba"
        "NB" -> "New Brunswick"
        "NL" -> "Newfoundland and Labrador"
        "NS" -> "Nova Scotia"
        "NT" -> "Northwest Territories"
        "NU" -> "Nunavut"
        "ON" -> "Ontario"
        "PE" -> "Prince Edward Island"
        "QC" -> "Quebec"
        "SK" -> "Saskatchewan"
        "YT" -> "Yukon"
        else -> code.uppercase()
    }
}
