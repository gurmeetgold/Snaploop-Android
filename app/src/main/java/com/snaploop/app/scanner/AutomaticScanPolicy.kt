package com.snaploop.app.scanner

import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.domain.EventParticipant
import com.snaploop.app.media.PhotoAccessLevel
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import java.time.ZoneId

/**
 * Account-isolated automatic-sync identity helpers matching the pinned iOS
 * AutomaticSyncIdentityScope contract. No Firebase UID, phone number, face
 * embedding, template ID, or auth token is written into the persistence key.
 */
object AutomaticSyncIdentityScope {
    fun storageKey(prefix: String, sourceInstallationId: String, eventId: String): String? {
        val source = sourceInstallationId.trim()
        if (source.isEmpty()) return null
        return "$prefix$source.$eventId"
    }

    fun participantEpoch(participant: EventParticipant): String {
        participant.membershipId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Legacy fallback mirrors iOS leave/rejoin semantics. Production manifests
        // require membership IDs, but keeping this deterministic prevents a stable
        // face identity from masking a legacy membership generation change.
        return "legacy-${participant.joinedAtMillis / 1000.0}"
    }

    fun scanTriggerFingerprint(
        event: SnapEvent,
        participants: List<EventParticipant>,
        sourceMembershipId: String? = null,
        sharingRevision: String? = null,
    ): String {
        val roster = participants
            .map {
                "${it.userId}=${participantEpoch(it)}=${it.stableFaceIdentityId}@${it.faceProfileRevision}"
            }
            .sorted()
            .joinToString(";")
        val sourceMembership = sourceMembershipId?.trim().orEmpty()
        val sharing = sharingRevision?.trim().orEmpty()
        return listOf(
            event.updatedAt.toEpochMilli().toString(),
            event.startsAt.toEpochMilli().toString(),
            event.endsAt.toEpochMilli().toString(),
            "source=$sourceMembership",
            "sharing=$sharing",
            roster,
        ).joinToString("::")
    }
}

/**
 * Pure iOS-parity policy for opportunistic foreground scanning.
 *
 * Automatic scans are intentionally conservative: only Events still inside
 * their Event/grace photo window, only while the member is sharing, only with
 * readable photo access, never in power-save mode, and normally no more than
 * once per Event per 30 minutes. A changed Event, trusted biometric roster,
 * membership generation, or sharing/own-match generation bypasses the cooldown
 * so newly eligible recipient work is not delayed.
 */
object AutomaticScanPolicy {
    const val COOLDOWN_MILLIS: Long = 30L * 60L * 1000L

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

    /**
     * Full pinned-iOS automatic-sync generation. Roster and source-membership
     * changes must bypass the otherwise persistent 30-minute cooldown.
     */
    fun triggerFingerprint(
        event: SnapEvent,
        participants: List<EventParticipant>,
        sourceMembershipId: String?,
        preferenceRevision: String,
    ): String = AutomaticSyncIdentityScope.scanTriggerFingerprint(
        event = event,
        participants = participants,
        sourceMembershipId = sourceMembershipId,
        sharingRevision = preferenceRevision,
    )

    /**
     * Kept for callers/tests that only need to compare Event/preference changes.
     * Automatic orchestration must use the roster-aware overload above.
     */
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
