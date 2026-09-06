package com.snaploop.app.domain

import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceAppearance
import com.snaploop.app.core.FaceModelPolicy

/** Private v5 biometric profile. Raw enrollment images never belong in this model. */
data class FaceProfile(
    val userId: String,
    val faceIdentityId: String?,
    val embedding: FloatArray,
    val templates: List<FaceTemplateRecord>,
    val version: Int,
    val updatedAtMillis: Long,
) {
    val stableFaceIdentityId: String get() = faceIdentityId?.trim().orEmpty()
    val faceProfileRevision: String
        get() {
            val ids = templates.map { it.id }.filter { it.isNotBlank() }.sorted()
            return if (ids.isEmpty()) "" else "v$version:${ids.joinToString("|")}" 
        }

    fun effectiveEmbeddings(): List<FloatArray> {
        if (templates.isEmpty()) return listOf(embedding)
        val distinct = FaceTemplatePose.entries.mapNotNull { pose ->
            templates.filter { it.pose == pose && it.quality.isFinite() }
                .maxWithOrNull(compareBy<FaceTemplateRecord> { it.quality }.thenBy { it.createdAtMillis })
                ?.embedding
        }
        return distinct.ifEmpty { listOf(embedding) }
    }

    init {
        require(userId.isNotBlank())
        require(version > 0)
        require(embedding.size == FaceModelPolicy.EMBEDDING_DIMENSION)
    }
}

data class PhotoMatchAppearance(
    val participantUserId: String,
    val recipientMembershipId: String?,
    val confidence: Double,
    val faceIdentityId: String?,
    val faceProfileRevision: String,
    val dismissedByUser: Boolean = false,
)

data class RecipientContext(
    val participantUserId: String,
    val recipientMembershipId: String?,
    val faceIdentityId: String,
    val faceProfileRevision: String,
)

/** Shared backend photo identity. Android source URIs are intentionally absent. */
data class PhotoMatch(
    val id: String,
    val eventId: String,
    val ownerUserId: String,
    val sourceInstallationId: String?,
    val sourceMembershipId: String?,
    val assetLocalId: String,
    val appearances: List<PhotoMatchAppearance>,
    val recipientRemovals: List<RecipientContext> = emptyList(),
    val capturedAtMillis: Long,
    val matchedAtMillis: Long,
    val thumbnailPath: String? = null,
) {
    val isSourceScopedIdentity: Boolean
        get() = !sourceInstallationId.isNullOrBlank() && id == sourceScopedId(eventId, sourceInstallationId, assetLocalId)

    companion object {
        fun sourceScopedId(eventId: String, sourceInstallationId: String, assetLocalId: String): String =
            "$eventId:${sourceInstallationId.trim()}:$assetLocalId"

        fun fromMatcher(
            eventId: String,
            ownerUserId: String,
            sourceInstallationId: String,
            sourceMembershipId: String?,
            assetLocalId: String,
            appearances: List<FaceAppearance>,
            recipientRemovals: List<RecipientContext>,
            capturedAtMillis: Long,
            matchedAtMillis: Long,
        ): PhotoMatch = PhotoMatch(
            id = sourceScopedId(eventId, sourceInstallationId, assetLocalId),
            eventId = eventId,
            ownerUserId = ownerUserId,
            sourceInstallationId = sourceInstallationId.trim(),
            sourceMembershipId = sourceMembershipId?.trim()?.takeIf { it.isNotEmpty() },
            assetLocalId = assetLocalId,
            appearances = appearances.map {
                PhotoMatchAppearance(
                    participantUserId = it.participantUserId,
                    recipientMembershipId = it.recipientMembershipId?.trim()?.takeIf(String::isNotEmpty),
                    confidence = it.confidence,
                    faceIdentityId = it.faceIdentityId,
                    faceProfileRevision = it.faceProfileRevision,
                )
            },
            recipientRemovals = recipientRemovals,
            capturedAtMillis = capturedAtMillis,
            matchedAtMillis = matchedAtMillis,
        )
    }
}

/** Same-person replacement gate used before asking the server to rotate template revision. */
object FaceProfileReplacementPolicy {
    fun isSameIdentity(newProfile: FaceProfile, existing: FaceProfile, threshold: Double = FaceModelPolicy.EVALUATION_MATCH_THRESHOLD): Boolean {
        val newEmbeddings = newProfile.templates.filter { it.pose != FaceTemplatePose.IMPORTED }.map { it.embedding }
        if (newEmbeddings.size < 3) return false
        val oldEmbeddings = existing.effectiveEmbeddings()
        if (oldEmbeddings.isEmpty()) return false
        var accepted = 0
        for (candidate in newEmbeddings) {
            val similarities = oldEmbeddings.mapNotNull { Embeddings.cosine(candidate, it) }
            val evaluation = com.snaploop.app.core.FaceTemplateMatchPolicy.evaluate(similarities, threshold)
            if (evaluation?.accepted == true) accepted++
        }
        val required = maxOf(2, kotlin.math.ceil(newEmbeddings.size * 0.60).toInt())
        return accepted >= required
    }
}
