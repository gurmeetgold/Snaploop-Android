package com.snaploop.app.scanner

import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.model.SnapEvent
import java.time.Instant

/**
 * Eligibility for the direct, best-effort corpus replay performed after the
 * current member authoritatively enables own-photo matches.
 *
 * The replay is intentionally bounded by CameraSyncCoordinator and is only an
 * optimization after the preference save: failure must never roll the saved
 * preference back or surface as a toggle failure.
 */
object OwnMatchReplayPolicy {
    fun shouldReplay(
        previousEnabled: Boolean,
        savedEnabled: Boolean,
        sharingEnabled: Boolean,
        event: SnapEvent,
        now: Instant,
        gracePeriodDays: Int = RemoteConfigValues().eventGracePeriodDays,
    ): Boolean =
        !previousEnabled &&
            savedEnabled &&
            sharingEnabled &&
            AutomaticScanPolicy.isWithinSyncWindow(event, now, gracePeriodDays)
}
