package com.snaploop.app.scanner

import com.snaploop.app.media.PhotoAccessLevel
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant

/**
 * Pure iOS-parity policy for opportunistic foreground scanning.
 *
 * Automatic scans are intentionally conservative: only currently-live Events,
 * only while the member is sharing, only with readable photo access, never in
 * power-save mode, and no more than once per Event per hour. Manual scans are
 * not subject to this cooldown.
 */
object AutomaticScanPolicy {
    const val COOLDOWN_MILLIS: Long = 60L * 60L * 1000L

    fun isLive(event: SnapEvent, now: Instant): Boolean =
        event.status == EventStatus.active &&
            !now.isBefore(event.startsAt) &&
            !now.isAfter(event.endsAt)

    fun shouldRun(
        event: SnapEvent,
        now: Instant,
        lastAutomaticScanAtMillis: Long?,
        sharingEnabled: Boolean,
        photoAccess: PhotoAccessLevel,
        powerSaveMode: Boolean,
    ): Boolean {
        if (!isLive(event, now)) return false
        if (!sharingEnabled) return false
        if (!photoAccess.canRead) return false
        if (powerSaveMode) return false

        val last = lastAutomaticScanAtMillis ?: return true
        return now.toEpochMilli() - last >= COOLDOWN_MILLIS
    }
}
