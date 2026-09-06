package com.snaploop.app.scanner

import com.snaploop.app.core.DetectedEmbedding

/** Candidate photo-face embeddings are device-local only and never published. */
data class CachedPhotoFace(val embedding: FloatArray, val sizeFraction: Double) {
    fun detected() = DetectedEmbedding(embedding, sizeFraction)
}

data class PhotoCorpusRecord(
    val assetId: String,
    val creationDateMillis: Long,
    val faces: List<CachedPhotoFace>,
    val processedAtMillis: Long,
)

class RecipientMatchCursor(
    val userId: String,
    var membershipEpoch: String,
    var faceIdentityId: String,
    var faceProfileRevision: String,
    val positiveAssetIds: MutableSet<String> = linkedSetOf(),
    val negativeAssetIds: MutableSet<String> = linkedSetOf(),
    val staleAssetIds: MutableSet<String> = linkedSetOf(),
) {
    fun hasEvaluated(assetId: String) = assetId !in staleAssetIds && (assetId in positiveAssetIds || assetId in negativeAssetIds)
    fun wasMatched(assetId: String) = assetId in positiveAssetIds

    fun reconcile(newMembershipEpoch: String, newFaceIdentityId: String, newFaceProfileRevision: String) {
        if (membershipEpoch != newMembershipEpoch || faceIdentityId != newFaceIdentityId) {
            membershipEpoch = newMembershipEpoch
            faceIdentityId = newFaceIdentityId
            faceProfileRevision = newFaceProfileRevision
            positiveAssetIds.clear(); negativeAssetIds.clear(); staleAssetIds.clear()
            return
        }
        if (faceProfileRevision != newFaceProfileRevision) {
            faceProfileRevision = newFaceProfileRevision
            markAllEvaluatedStale()
        }
    }

    fun mark(assetId: String, matched: Boolean) {
        if (matched) { positiveAssetIds += assetId; negativeAssetIds -= assetId }
        else { negativeAssetIds += assetId; positiveAssetIds -= assetId }
        staleAssetIds -= assetId
    }

    fun clearPositives() = positiveAssetIds.clear()
    fun clearNegatives() = negativeAssetIds.clear()
    fun markAllEvaluatedStale() { staleAssetIds += positiveAssetIds; staleAssetIds += negativeAssetIds }
    fun retainAssetIds(validIds: Set<String>) { positiveAssetIds.retainAll(validIds); negativeAssetIds.retainAll(validIds); staleAssetIds.retainAll(validIds) }
}

class ScanState(
    val eventId: String,
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val photoCorpus: MutableMap<String, PhotoCorpusRecord> = linkedMapOf(),
    val recipientCursors: MutableMap<String, RecipientMatchCursor> = linkedMapOf(),
    var sourceMembershipEpoch: String? = null,
    var sourceSharingRevision: String? = null,
    var sourceOwnMatchesRevision: String? = null,
    var rosterAmbiguityRevision: String? = null,
    var lastSyncedAtMillis: Long? = null,
) {
    fun reconcileRecipient(userId: String, membershipEpoch: String, faceIdentityId: String, faceProfileRevision: String) {
        recipientCursors[userId]?.reconcile(membershipEpoch, faceIdentityId, faceProfileRevision)
            ?: run { recipientCursors[userId] = RecipientMatchCursor(userId, membershipEpoch, faceIdentityId, faceProfileRevision) }
    }
    fun retainRecipientCursors(activeUserIds: Set<String>) { recipientCursors.keys.retainAll(activeUserIds) }
    fun resetRecipientCursors() = recipientCursors.clear()
    fun clearPositiveRecipientEvaluations() = recipientCursors.values.forEach(RecipientMatchCursor::clearPositives)
    fun clearNegativeRecipientEvaluations() = recipientCursors.values.forEach(RecipientMatchCursor::clearNegatives)
    fun markAllRecipientEvaluationsStale() = recipientCursors.values.forEach(RecipientMatchCursor::markAllEvaluatedStale)
    fun pendingRecipientUserIds(assetId: String, among: Set<String>): Set<String> = among.filterTo(linkedSetOf()) { recipientCursors[it]?.hasEvaluated(assetId) != true }
    fun markRecipientEvaluation(userId: String, assetId: String, matched: Boolean) { recipientCursors[userId]?.mark(assetId, matched) }
    fun retainCurrentAssets(validIds: Set<String>) {
        photoCorpus.keys.retainAll(validIds)
        recipientCursors.values.forEach { it.retainAssetIds(validIds) }
    }
    companion object { const val CURRENT_SCHEMA_VERSION = 5 }
}
