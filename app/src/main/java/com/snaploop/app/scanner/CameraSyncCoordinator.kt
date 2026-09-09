package com.snaploop.app.scanner

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.snaploop.app.core.FaceMatcher
import com.snaploop.app.core.MatchConfig
import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.data.EventFaceProfileClient
import com.snaploop.app.data.FirebaseMatchRepository
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.domain.RecipientContext
import com.snaploop.app.face.AndroidFacePipeline
import com.snaploop.app.media.MediaStorePhotoLibrary
import com.snaploop.app.security.AccountInstallationIdentityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Event scanner shared by manual and foreground-automatic orchestration.
 * No continuous/background gallery inspection.
 * Recipient eligibility mirrors iOS, including the explicit include-own-matches
 * preference so a user's own photos are evaluated only when they opt in.
 */
class CameraSyncCoordinator(context: Context) : AutoCloseable {
    data class Progress(val checked: Int, val total: Int, val published: Int)
    data class Result(
        val checked: Int,
        val published: Int,
        val remaining: Int,
        val failed: Int = 0,
    ) {
        val hasRetryableFailures: Boolean get() = failed > 0
    }

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val library = MediaStorePhotoLibrary(appContext)
    private val faces = AndroidFacePipeline(appContext)
    private val rosterClient = EventFaceProfileClient()
    private val matches = FirebaseMatchRepository()
    private val states = EncryptedScanStateStore(appContext)
    private val installationIdentity = AccountInstallationIdentityStore(appContext)

    suspend fun scan(
        eventId: String,
        startMillis: Long,
        endMillis: Long,
        sharingEnabled: Boolean,
        includeOwnMatches: Boolean = false,
        ownMatchesRevision: String? = null,
        config: RemoteConfigValues,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        val cancellationToken = ScanCancellationRegistry.start(eventId)
        coroutineContext.ensureActive()
        ScanCancellationRegistry.ensureActive(cancellationToken)

        val uid = auth.currentUser?.uid ?: error("Authentication is required")
        val manifest = rosterClient.manifest(eventId)
        ScanCancellationRegistry.ensureActive(cancellationToken)

        val sourceMembershipId =
            manifest.sourceMembershipId ?: error("Current Event membership is unavailable")
        val participants = manifest.participants.filter {
            it.membershipId != null &&
                it.faceIdentityId?.isNotBlank() == true &&
                it.faceProfileRevision.isNotBlank() &&
                (it.userId != uid || includeOwnMatches)
        }
        val matcherParticipants = participants.map { it.asMatcherParticipant() }
        val matchableIds = participants.mapTo(linkedSetOf()) { it.userId }
        val rosterRevision = participants.sortedBy { it.userId }.joinToString(";") {
            "${it.userId}|${it.membershipId}|${it.stableFaceIdentityId}|${it.faceProfileRevision}"
        }
        val state = states.load(eventId)
        if (state.sourceMembershipEpoch != sourceMembershipId) {
            state.sourceMembershipEpoch = sourceMembershipId
            state.resetRecipientCursors()
        }
        participants.forEach {
            state.reconcileRecipient(
                it.userId,
                it.membershipId!!,
                it.stableFaceIdentityId,
                it.faceProfileRevision,
            )
        }

        // When own-photo visibility is disabled, dropping the current user's cursor ensures no
        // self appearance is published. Turning it back on creates a fresh cursor and replays the
        // encrypted local corpus without requiring face extraction again.
        state.retainRecipientCursors(matchableIds)
        val normalizedOwnRevision = ownMatchesRevision?.trim()?.takeIf { it.isNotEmpty() }
        if (
            includeOwnMatches &&
            state.sourceOwnMatchesRevision != null &&
            normalizedOwnRevision != null &&
            state.sourceOwnMatchesRevision != normalizedOwnRevision
        ) {
            state.recipientCursors[uid]?.markAllEvaluatedStale()
        }
        state.sourceOwnMatchesRevision = normalizedOwnRevision ?: includeOwnMatches.toString()

        if (state.rosterAmbiguityRevision != null && state.rosterAmbiguityRevision != rosterRevision) {
            state.markAllRecipientEvaluationsStale()
        }
        state.rosterAmbiguityRevision = rosterRevision

        val sharingRevision = if (sharingEnabled) "on" else "off"
        if (
            state.sourceSharingRevision != null &&
            state.sourceSharingRevision != sharingRevision &&
            sharingEnabled
        ) {
            // Server-side sharing OFF removes source rows; OFF -> ON must replay previously-positive
            // recipients so those matches can be republished.
            state.clearPositiveRecipientEvaluations()
        }
        state.sourceSharingRevision = sharingRevision

        ScanCancellationRegistry.ensureActive(cancellationToken)
        val assets = library.assets(startMillis, endMillis)
        val validIds = assets.mapTo(hashSetOf()) { it.id }
        state.retainCurrentAssets(validIds)
        val sourceInstallationId = installationIdentity.idFor(uid)
        val matcher = FaceMatcher(
            MatchConfig(
                config.matchConfidenceThreshold,
                config.matchAmbiguityMargin,
                config.minFaceSizeFraction,
            ),
        )
        val pendingAssets = assets.filter { asset ->
            state.pendingRecipientUserIds(asset.id, matchableIds).isNotEmpty()
        }
        val batch = pendingAssets.take(config.maxAssetsPerSyncBatch.coerceAtLeast(0))
        var published = 0
        var completed = 0
        var failed = 0

        for (asset in batch) {
            coroutineContext.ensureActive()
            ScanCancellationRegistry.ensureActive(cancellationToken)

            try {
                // Keep the normalized bytes for this iteration so a newly-processed photo is not
                // read from MediaStore a second time just to publish its thumbnail.
                var sourceJpeg: ByteArray? = null
                var corpus = state.photoCorpus[asset.id]
                if (corpus == null) {
                    sourceJpeg = library.normalizedJpeg(
                        asset,
                        config.thumbnailMaxPixelSize,
                        (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                    )

                    ScanCancellationRegistry.ensureActive(cancellationToken)
                    val detected = faces.detectFaces(sourceJpeg)
                    corpus = PhotoCorpusRecord(
                        asset.id,
                        asset.creationDateMillis,
                        detected.map { CachedPhotoFace(it.embedding, it.sizeFraction) },
                        System.currentTimeMillis(),
                    )
                    state.photoCorpus[asset.id] = corpus

                    // Persist expensive on-device extraction before any network publication.
                    // A transient upload failure can then retry from cached faces instead of
                    // decoding and embedding the same source photo again.
                    states.save(state)
                }

                val pendingIds = state.pendingRecipientUserIds(asset.id, matchableIds)
                if (pendingIds.isNotEmpty()) {
                    val appearances = matcher.appearances(
                        corpus.faces.map(CachedPhotoFace::detected),
                        matcherParticipants,
                    )
                    val appearancesByUser = appearances.associateBy { it.participantUserId }
                    val publishAppearances = appearances.filter { it.participantUserId in pendingIds }
                    val removals = mutableListOf<RecipientContext>()
                    for (participant in participants.filter { it.userId in pendingIds }) {
                        val cursor = state.recipientCursors[participant.userId] ?: continue
                        val wasMatched = cursor.wasMatched(asset.id)
                        val nowMatched = appearancesByUser.containsKey(participant.userId)
                        if (wasMatched && !nowMatched) {
                            removals += RecipientContext(
                                participant.userId,
                                participant.membershipId,
                                participant.stableFaceIdentityId,
                                participant.faceProfileRevision,
                            )
                        }
                    }

                    ScanCancellationRegistry.ensureActive(cancellationToken)
                    if (sharingEnabled && (publishAppearances.isNotEmpty() || removals.isNotEmpty())) {
                        val thumbnail = if (publishAppearances.isNotEmpty()) {
                            sourceJpeg ?: library.normalizedJpeg(
                                asset,
                                config.thumbnailMaxPixelSize,
                                (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                            )
                        } else {
                            byteArrayOf()
                        }

                        ScanCancellationRegistry.ensureActive(cancellationToken)
                        matches.upload(
                            PhotoMatch.fromMatcher(
                                eventId,
                                uid,
                                sourceInstallationId,
                                sourceMembershipId,
                                asset.id,
                                publishAppearances,
                                removals,
                                asset.creationDateMillis,
                                System.currentTimeMillis(),
                            ),
                            thumbnail,
                        )
                        published++
                    }

                    // Advance recipient outcomes only after any required publication/removal
                    // succeeds. A failed asset therefore remains pending with its previous
                    // positive bit intact and can be retried safely.
                    for (participant in participants.filter { it.userId in pendingIds }) {
                        state.markRecipientEvaluation(
                            participant.userId,
                            asset.id,
                            appearancesByUser.containsKey(participant.userId),
                        )
                    }
                }

                completed++
                state.lastSyncedAtMillis = System.currentTimeMillis()
                states.save(state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // One stale MediaStore row or transient backend failure must not terminate the
                // entire Event pass. The cursor was not advanced, so this asset remains retryable.
                failed++
            }

            onProgress(Progress(completed, batch.size, published))
        }

        state.lastSyncedAtMillis = System.currentTimeMillis()
        states.save(state)
        val remaining = ScanPassPolicy.remainingAfterPass(
            pendingBeforePass = pendingAssets.size,
            attemptedThisPass = batch.size,
            failedThisPass = failed,
        )
        return Result(
            checked = completed,
            published = published,
            remaining = remaining,
            failed = failed,
        )
    }

    override fun close() {
        faces.close()
    }
}
