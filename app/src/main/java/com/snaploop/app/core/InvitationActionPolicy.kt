package com.snaploop.app.core

/** Pure dispatch policy so accept/decline links cannot collapse into ordinary review. */
object InvitationActionPolicy {
    enum class Dispatch { REVIEW, ACCEPT, DECLINE }

    fun dispatch(action: InviteAction): Dispatch = when (action) {
        InviteAction.REVIEW -> Dispatch.REVIEW
        InviteAction.ACCEPT -> Dispatch.ACCEPT
        InviteAction.DECLINE -> Dispatch.DECLINE
    }
}
