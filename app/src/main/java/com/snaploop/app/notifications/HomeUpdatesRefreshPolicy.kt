package com.snaploop.app.notifications

/** Pure reload-key policy so Home manual refresh remains deterministic and testable. */
object HomeUpdatesRefreshPolicy {
    fun shouldReload(
        previousUserId: String?,
        previousGeneration: Int,
        currentUserId: String?,
        currentGeneration: Int,
    ): Boolean {
        val current = currentUserId?.trim().orEmpty()
        if (current.isEmpty()) return false
        val previous = previousUserId?.trim().orEmpty()
        return previous != current || previousGeneration != currentGeneration
    }
}
