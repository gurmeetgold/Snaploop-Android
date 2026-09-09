package com.snaploop.app.ui

/**
 * Stable Invite-by-Phone geometry and presentation values mirrored from the
 * pinned iOS InvitePeopleView. Keeping them testable prevents the Android
 * controls from drifting back toward default outlined Material treatments.
 */
internal object PhoneInviteVisualParitySpec {
    const val COUNTRY_SELECTOR_HEIGHT_DP = 58
    const val PHONE_FIELD_HEIGHT_DP = 58
    const val CONTROL_RADIUS_DP = 15
    const val COUNTRY_HORIZONTAL_PADDING_DP = 11
    const val COUNTRY_FILL_ALPHA = 0.18f

    const val CONTACT_BUTTON_HEIGHT_DP = 48
    const val CONTACT_FILL_ALPHA = 0.11f

    const val SEND_DISABLED_ALPHA = 0.50f

    const val STATUS_ICON_SIZE_DP = 34
    const val STATUS_ICON_FILL_ALPHA = 0.12f
    const val STATUS_CAPSULE_HORIZONTAL_PADDING_DP = 8
    const val STATUS_CAPSULE_VERTICAL_PADDING_DP = 5
    const val STATUS_CAPSULE_FILL_ALPHA = 0.22f
}
