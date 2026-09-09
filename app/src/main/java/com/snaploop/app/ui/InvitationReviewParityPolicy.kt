package com.snaploop.app.ui

import com.snaploop.app.core.InviteAction
import com.snaploop.app.model.EventMember

/** Pure invitation presentation semantics mirrored from pinned iOS JoinEventView. */
internal object InvitationReviewParityPolicy {
    fun inviterLabel(
        previewName: String?,
        members: List<EventMember>,
        organizerUserId: String,
    ): String? {
        val preview = previewName?.trim()?.takeIf { it.isNotEmpty() }
        if (preview != null) return preview
        return members
            .firstOrNull { it.userId == organizerUserId }
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun automaticProgressCopy(action: InviteAction): String = when (action) {
        InviteAction.ACCEPT -> "Joining Event…"
        InviteAction.DECLINE -> "Declining invitation…"
        InviteAction.REVIEW -> "Opening invitation…"
    }
}
