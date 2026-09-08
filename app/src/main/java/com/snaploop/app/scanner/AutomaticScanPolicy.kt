package com.snaploop.app.scanner

import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.media.PhotoAccessLevel
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import java.time.ZoneId

/**
 * Pure iOS-parity policy for opportunistic foreground scanning.
 *
 * Automatic scans are intentionally conservative: only Events still inside
 * their Event/grace photo window, only while the member is sharing, only with
 * readable photo access, never in power-save mode, and normally no more than
 * once per Event per hour. A changed Event or sharing/own-match generation
 * bypasses the cooldown so newly eligible recipient work is not delayed.
 */
object AutomaticScanPolicy {
    const val COOLDOWN_MILLIS: Long = 60L * 60L * 1000L

    fun isWithinSyncWindow(
        event: SnapEvent,
        now: Instant,
        gracePeriodDays: Int = RemoteConfigValues().eventGracePeriodDays,
    ): Boolean {
        if (event.status != EventStatus.active || now.isBefore(event.startsAt)) return false
        val zone = runCatching {
            ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
        }.getOrDefault(ZoneId.systemDefault())
        val graceEnd = event.endsAt
            .atZone(zone)
            .plusDays(gracePeriodDays.coerceAtLeast(0).toLong())
            .toInstant()
        return !now.isAfter(graceEnd)
    }

    fun triggerFingerprint(event: SnapEvent, preferenceRevision: String): String = listOf(
        event.updatedAt.toEpochMilli().toString(),
        event.startsAt.toEpochMilli().toString(),
        event.endsAt.toEpochMilli().toString(),
        preferenceRevision.trim(),
    ).joinToString("::")

    fun shouldRun(
        event: SnapEvent,
        now: Instant,
        lastAutomaticScanAtMillis: Long?,
        sharingEnabled: Boolean,
        photoAccess: PhotoAccessLevel,
        powerSaveMode: Boolean,
        gracePeriodDays: Int = RemoteConfigValues().eventGracePeriodDays,
        triggerChanged: Boolean = false,
    ): Boolean {
        if (!isWithinSyncWindow(event, now, gracePeriodDays)) return false
        if (!sharingEnabled) return false
        if (!photoAccess.canRead) return false
        if (powerSaveMode) return false
        if (triggerChanged) return true

        val last = lastAutomaticScanAtMillis ?: return true
        return now.toEpochMilli() - last >= COOLDOWN_MILLIS
    }
}
