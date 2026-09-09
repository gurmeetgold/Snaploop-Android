package com.snaploop.app.ui

/** Pure presentation policy mirrored from pinned iOS PrivacyView consent states. */
internal object PrivacyConsentParityPolicy {
    data class Presentation(
        val statusText: String,
        val canWithdraw: Boolean,
    )

    fun presentation(consentActive: Boolean?): Presentation = when (consentActive) {
        true -> Presentation("Active · Review or withdraw", canWithdraw = true)
        false -> Presentation("Not active · Review details", canWithdraw = false)
        null -> Presentation("Checking Face Match consent…", canWithdraw = false)
    }
}
