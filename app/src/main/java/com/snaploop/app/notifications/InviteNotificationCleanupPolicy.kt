package com.snaploop.app.notifications

import com.snaploop.app.core.DeepLinkParser

/** Pure predicate used to mirror iOS Event-scoped invite-notification cleanup. */
object InviteNotificationCleanupPolicy {
    fun shouldCancel(
        targetEventId: String,
        payloadEventId: String?,
        inviteToken: String?,
    ): Boolean {
        val target = SnapLoopNotificationContract.normalizeEventId(targetEventId) ?: return false
        val payloadEvent = SnapLoopNotificationContract.normalizeEventId(payloadEventId) ?: return false
        val token = DeepLinkParser.normalizeToken(inviteToken) ?: return false
        return target == payloadEvent && token.isNotEmpty()
    }
}
