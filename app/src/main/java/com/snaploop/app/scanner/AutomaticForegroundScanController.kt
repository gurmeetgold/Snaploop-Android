package com.snaploop.app.scanner

import android.content.Context
import android.os.PowerManager
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.core.SnapLoopRemoteConfig
import com.snaploop.app.data.MemberPhotoPreferencesClient
import com.snaploop.app.media.PhotoAccessState
import com.snaploop.app.model.SnapEvent
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
 * Opportunistic foreground Event scanning, mirroring the current iOS lifecycle.
 *
 * The UI asks this controller to run when the authenticated app becomes active
 * and whenever the authenticated Event/preference snapshot changes. The
 * controller enforces Event+grace eligibility, sharing, photo access, battery
 * saver avoidance, a one-hour per-Event cooldown, and one bounded scanner batch.
 * Event or sharing/own-match generation changes bypass the cooldown so newly
 * eligible recipient work is replayed immediately from the protected corpus.
 *
 * It deliberately does not run a service or WorkManager job: Android must not
 * inspect the photo library continuously in the background.
 */
class AutomaticForegroundScanController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memberPreferences = MemberPhotoPreferencesClient()
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

        val now = Instant.now()
        val config = remoteConfig()
        CameraSyncCoordinator(appContext).use { scanner ->
            for (event in events.sortedBy { it.startsAt }) {
                val preference = runCatching { memberPreferences.load(event.id) }.getOrNull() ?: continue
                val lastRun = preferences
                    .getLong(lastRunKey(event.id), NO_TIMESTAMP)
                    .takeUnless { it == NO_TIMESTAMP }
                val fingerprint = AutomaticScanPolicy.triggerFingerprint(event, preference.revisionToken)
                val triggerChanged = preferences.getString(fingerprintKey(event.id), null) != fingerprint

                if (
                    !AutomaticScanPolicy.shouldRun(
                        event = event,
                        now = now,
                        lastAutomaticScanAtMillis = lastRun,
                        sharingEnabled = preference.sharingEnabled,
                        photoAccess = photoAccess,
                        powerSaveMode = powerManager.isPowerSaveMode,
                        gracePeriodDays = config.eventGracePeriodDays,
                        triggerChanged = triggerChanged,
                    )
                ) continue

                scanner.scan(
                    eventId = event.id,
                    startMillis = event.startsAt.toEpochMilli(),
                    endMillis = event.endsAt.toEpochMilli(),
                    sharingEnabled = true,
                    includeOwnMatches = preference.includeOwnMatches,
                    ownMatchesRevision = preference.revisionToken,
                    config = config,
                )

                preferences.edit()
                    .putLong(lastRunKey(event.id), System.currentTimeMillis())
                    .putString(fingerprintKey(event.id), fingerprint)
                    .apply()
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

    private fun lastRunKey(eventId: String) = "last_auto_scan.$eventId.v1"
    private fun fingerprintKey(eventId: String) = "fingerprint.$eventId.v2"

    private companion object {
        const val PREFS = "snaploop.auto.scan"
        const val NO_TIMESTAMP = Long.MIN_VALUE
    }
}
