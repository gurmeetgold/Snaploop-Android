package com.snaploop.app.ui

/** Pure presentation classification for the pinned iOS Scan Photos completion states. */
object ScanResultPresentationPolicy {
    enum class Kind { RETRYABLE_FAILURE, DEFERRED_BATCH, COMPLETE }

    fun kind(remaining: Int, failed: Int, checked: Int): Kind = when {
        // “Scan stopped” is reserved for a pass that could not process a single asset. A stale
        // MediaStore row or one transient publication failure must not tell the user they stopped
        // a scan that actually made progress; the pending item simply remains retryable.
        failed > 0 && checked == 0 -> Kind.RETRYABLE_FAILURE
        remaining > 0 -> Kind.DEFERRED_BATCH
        else -> Kind.COMPLETE
    }

    fun retryMessage(remaining: Int): String {
        val count = remaining.coerceAtLeast(1)
        val noun = if (count == 1) "photo is" else "photos are"
        return "$count $noun still pending because the scan could not finish processing or sharing them. " +
            "Nothing failed is marked as complete. Try again."
    }
}
