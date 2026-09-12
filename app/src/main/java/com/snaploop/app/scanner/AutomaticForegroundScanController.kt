package com.snaploop.app.scanner

import android.content.Context
import android.os.PowerManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.core.SnapLoopRemoteConfig
import com.snaploop.app.data.EventFaceProfileClient
import com.snaploop.app.data.MemberPhotoPreferencesClient
import com.snaploop.app.media.PhotoAccessState
import com.snaploop.app.model.SnapEvent
import com.snaploop.app.security.AccountInstallationIdentityStore
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opportunistic foreground Event scanning, mirroring the pinned iOS lifecycle.
 *
 * The UI asks this controller to run when the authenticated app becomes active
 * and whenever the authenticated Event/preference snapshot changes. The
 * controller enforces Event+grace eligibility, sharing, photo access, battery
 * saver avoidance, a persistent 30-minute per-Event cooldown, and one bounded
 * scanner batch. Event, trusted roster, membership, or sharing/own-match
 * generation changes bypass the cooldown so newly eligible recipient work is
 * replayed immediately from the protected corpus.
 *
 * Cooldown/fingerprint persistence is scoped by the pseudonymous account +
 * installation identity. One account on a shared device therefore cannot
 * suppress another account's automatic Event scan.
 *
 * It deliberately does not run a service or WorkManager job: Android must not
 * inspect the photo library continuously in the background.
 */
class AutomaticForegroundScanController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memberPreferences = MemberPhotoPreferencesClient()
    private val rosterClient = EventFaceProfileClient()
    private val auth = FirebaseAuth.getInstance()
    private val installationIdentity = AccountInstallationIdentityStore(appContext)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeJob: Job? = null
    @Volatile
    private var activeSignature: String? = null
    @Volatile
    private var cachedConfig: RemoteConfigValues? = null

    @Synchronized
    fun request(events: List<SnapEvent>, triggerGeneration: String? = null) {
        val snapshot = events.toList()
        val signature = buildString {
            append(
                snapshot
                    .sortedBy { it.id }
                    .joinToString(";") { "${it.id}|${it.status}|${it.updatedAt}" },
            )
            append("|trigger=")
            append(triggerGeneration.orEmpty())
        }

        if (activeJob?.isActive == true && activeSignature == signature) return

        // A changed Event/preference snapshot (notably join, sharing generation,
        // or own-match visibility) should be considered immediately. Cancelling
        // the old pass is safe because the scanner checkpoints after each asset.
        activeJob?.cancel()
        activeSignature = signature
        activeJob = scope.launch {
            try {
                runPass(snapshot)
            } catch (_: CancellationException) {
                // Foreground loss / changed Event snapshot is an expected stop.
            } finally {
                synchronized(this@AutomaticForegroundScanController) {
                    if (activeSignature == signature) activeSignature = null
                }
            }
        }
    }

    @Synchronized
    fun cancelActive() {
        activeJob?.cancel()
        activeJob = null
        activeSignature = null
    }

    private suspend fun runPass(events: List<SnapEvent>) {
        if (powerManager.isPowerSaveMode) return

        val photoAccess = PhotoAccessState.current(appContext)
        if (!photoAccess.canRead) return

        val userId = auth.currentUser?.uid ?: return
        val sourceInstallationId = installationIdentity.idFor(userId)
        if (sourceInstallationId.isBlank()) return

        val now = Instant.now()
        val config = remoteConfig()
        CameraSyncCoordinator(appContext).use { scanner ->
            for (event in events.sortedBy { it.startsAt }) {
                if (!AutomaticScanPolicy.isWithinSyncWindow(event, now, config.eventGracePeriodDays)) continue

                val preference = runCatching { memberPreferences.load(event.id) }.getOrNull() ?: continue
                if (!preference.sharingEnabled) continue

                // Pinned iOS loads the trusted biometric manifest before applying
                // cooldown. A recipient template/identity change or source
                // leave+rejoin must therefore bypass an otherwise fresh cooldown.
                val manifest = runCatching { rosterClient.manifest(event.id) }.getOrNull() ?: continue
                val fingerprint = AutomaticScanPolicy.triggerFingerprint(
                    event = event,
                    participants = manifest.participants,
                    sourceMembershipId = manifest.sourceMembershipId,
                    preferenceRevision = preference.revisionToken,
                )
                val lastRun = preferences
                    .getLong(lastRunKey(sourceInstallationId, event.id), NO_TIMESTAMP)
                    .takeUnless { it == NO_TIMESTAMP }
                val triggerChanged = preferences.getString(
                    fingerprintKey(sourceInstallationId, event.id),
                    null,
                ) != fingerprint

                if (
                    !AutomaticScanPolicy.shouldRun(
                        event = event,
                        now = now,
                        lastAutomaticScanAtMillis = lastRun,
                        sharingEnabled = true,
                        photoAccess = photoAccess,
                        powerSaveMode = powerManager.isPowerSaveMode,
                        gracePeriodDays = config.eventGracePeriodDays,
                        triggerChanged = triggerChanged,
                    )
                ) continue

                val result = scanner.scan(
                    eventId = event.id,
                    startMillis = event.startsAt.toEpochMilli(),
                    endMillis = event.endsAt.toEpochMilli(),
                    sharingEnabled = true,
                    includeOwnMatches = preference.includeOwnMatches,
                    ownMatchesRevision = preference.revisionToken,
                    config = config,
                    manifestOverride = manifest,
                )

                // Match the already-established Android recovery rule: a pass
                // that leaves an attempted asset retryable must not be hidden
                // behind the normal automatic cooldown. This preserves the green
                // retry behavior verified before this parity pass.
                if (ScanPassPolicy.shouldCheckpointAutomaticPass(result.failed)) {
                    preferences.edit()
                        .putLong(lastRunKey(sourceInstallationId, event.id), System.currentTimeMillis())
                        .putString(fingerprintKey(sourceInstallationId, event.id), fingerprint)
                        .apply()
                }
            }
        }
    }

    private suspend fun remoteConfig(): RemoteConfigValues {
        cachedConfig?.let { return it }
        val resolved = withTimeoutOrNull(5_000L) {
            runCatching {
                SnapLoopRemoteConfig(FirebaseRemoteConfig.getInstance()).initialize()
            }.getOrDefault(RemoteConfigValues())
        } ?: RemoteConfigValues()
        cachedConfig = resolved
        return resolved
    }

    override fun close() {
        cancelActive()
        scope.cancel()
    }

    private fun lastRunKey(sourceInstallationId: String, eventId: String): String =
        requireNotNull(
            AutomaticSyncIdentityScope.storageKey(
                prefix = LAST_RUN_PREFIX,
                sourceInstallationId = sourceInstallationId,
                eventId = eventId,
            ),
        )

    private fun fingerprintKey(sourceInstallationId: String, eventId: String): String =
        requireNotNull(
            AutomaticSyncIdentityScope.storageKey(
                prefix = FINGERPRINT_PREFIX,
                sourceInstallationId = sourceInstallationId,
                eventId = eventId,
            ),
        )

    private companion object {
        const val PREFS = "snaploop.auto.scan"
        const val LAST_RUN_PREFIX = "last_auto_scan.account.v2."
        const val FINGERPRINT_PREFIX = "fingerprint.account.v3."
        const val NO_TIMESTAMP = Long.MIN_VALUE
    }
}
