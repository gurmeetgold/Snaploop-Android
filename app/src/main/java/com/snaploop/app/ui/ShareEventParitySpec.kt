package com.snaploop.app.ui

/** Pure visual/interaction contract mirrored from pinned iOS ShareEventView.swift. */
internal object ShareEventParitySpec {
    const val BRAND_MARK_DP = 64
    const val CONTENT_SPACING_DP = 18
    const val QR_SIZE_DP = 220
    const val SECONDARY_ACTION_HEIGHT_DP = 50
    const val SECONDARY_ACTION_RADIUS_DP = 17
    const val PHONE_ICON_WELL_DP = 42
    const val COPY_FEEDBACK_DURATION_MS = 1_500L

    const val PHONE_INVITE_TITLE = "Invite by Phone or Contacts"
    const val PHONE_INVITE_SUBTITLE = "Existing users get an in-app invite; others can receive the link."

    enum class CopyAction { CODE, LINK }

    fun actionTitle(action: CopyAction, confirmed: Boolean): String = when {
        confirmed -> "Copied"
        action == CopyAction.CODE -> "Copy Code"
        else -> "Copy Link"
    }
}
