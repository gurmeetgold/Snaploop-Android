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
import com.snaploop.app.media.PhotoUnavailableException
import com.snaploop.app.security.AccountInstallationIdentityStore
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * User-triggered event scanner. No continuous/background gallery inspection.
 * Recipient eligibility mirrors iOS, including the explicit include-own-matches
 * preference so a user's own photos are evaluated only when they opt in.
 */
class CameraSyncCoordinator(context: Context) : AutoCloseable {
    data class Progress(val checked: Int, val total: Int, val published: Int)
    data class Result(val checked: Int, val published: Int, val remaining: Int)

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
        val uid = auth.currentUser?.uid ?: error("Authentication is required")
        val manifest = rosterClient.manifest(eventId)
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

        for ((index, asset) in batch.withIndex()) {
            coroutineContext.ensureActive()

            // Keep the normalized bytes for this iteration so a newly-processed photo is not read
            // from MediaStore a second time just to publish its thumbnail.
            var sourceJpeg: ByteArray? = null
            var corpus = state.photoCorpus[asset.id]
            if (corpus == null) {
                sourceJpeg = try {
                    library.normalizedJpeg(
                        asset,
                        config.thumbnailMaxPixelSize,
                        (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                    )
                } catch (_: PhotoUnavailableException) {
                    // MediaStore can return a row and revoke/remove it before the stream is opened
                    // (common with Android selected-photo access and OEM gallery providers). A
                    // single stale row must never terminate the whole Event scan.
                    onProgress(Progress(index + 1, batch.size, published))
                    continue
                }

                val detected = faces.detectFaces(sourceJpeg)
                corpus = PhotoCorpusRecord(
                    asset.id,
                    asset.creationDateMillis,
                    detected.map { CachedPhotoFace(it.embedding, it.sizeFraction) },
                    System.currentTimeMillis(),
                )
                state.photoCorpus[asset.id] = corpus
                states.save(state)
            }

            val pendingIds = state.pendingRecipientUserIds(asset.id, matchableIds)
            if (pendingIds.isEmpty()) {
                onProgress(Progress(index + 1, batch.size, published))
                continue
            }

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

            if (sharingEnabled && (publishAppearances.isNotEmpty() || removals.isNotEmpty())) {
                val thumbnail = if (publishAppearances.isNotEmpty()) {
                    sourceJpeg ?: try {
                        library.normalizedJpeg(
                            asset,
                            config.thumbnailMaxPixelSize,
                            (config.thumbnailJpegQuality * 100).toInt().coerceIn(1, 100),
                        )
                    } catch (_: PhotoUnavailableException) {
                        // The corpus can be cached even after the underlying photo is removed. Do
                        // not mark a positive as delivered when its preview could not be published.
                        onProgress(Progress(index + 1, batch.size, published))
                        continue
                    }
                } else {
                    byteArrayOf()
                }

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

            for (participant in participants.filter { it.userId in pendingIds }) {
                state.markRecipientEvaluation(
                    participant.userId,
                    asset.id,
                    appearancesByUser.containsKey(participant.userId),
                )
            }
            state.lastSyncedAtMillis = System.currentTimeMillis()
            states.save(state)
            onProgress(Progress(index + 1, batch.size, published))
        }
        return Result(batch.size, published, pendingAssets.size - batch.size)
    }

    override fun close() {
        faces.close()
    }
}
