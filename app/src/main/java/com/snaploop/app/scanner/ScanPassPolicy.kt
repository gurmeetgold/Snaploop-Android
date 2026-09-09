package com.snaploop.app.scanner

/**
 * Pure accounting rules shared by manual and automatic scanner orchestration.
 *
 * A failed asset was attempted but did not complete, so it remains pending in
 * addition to any work deferred by the per-pass batch cap. Automatic sync must
 * not checkpoint a failed pass as caught up; otherwise the normal cooldown can
 * suppress the retry even though device-local recipient work is still pending.
 */
object ScanPassPolicy {
    fun remainingAfterPass(
        pendingBeforePass: Int,
        attemptedThisPass: Int,
        failedThisPass: Int,
    ): Int {
        val deferred = (pendingBeforePass.coerceAtLeast(0) - attemptedThisPass.coerceAtLeast(0))
            .coerceAtLeast(0)
        return deferred + failedThisPass.coerceAtLeast(0)
    }

    fun shouldCheckpointAutomaticPass(failedThisPass: Int): Boolean = failedThisPass <= 0
}
