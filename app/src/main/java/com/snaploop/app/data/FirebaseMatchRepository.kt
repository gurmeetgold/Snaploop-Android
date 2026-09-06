package com.snaploop.app.data

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.domain.PhotoMatchAppearance
import kotlinx.coroutines.tasks.await

/** Production match metadata/optimized preview repository. Originals intentionally remain unavailable in v1 parity. */
class FirebaseMatchRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val callable: FirebaseCallableClient = FirebaseCallableClient(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    suspend fun upload(match: PhotoMatch, thumbnailJpeg: ByteArray) {
        val docId = documentId(match.id)
        val path = "events/${match.eventId}/photos/${match.ownerUserId}/$docId/thumbnail.jpg"
        val ref = storage.reference.child(path)
        val appearances = match.appearances.map {
            val identity = it.faceIdentityId?.trim()?.takeIf(String::isNotEmpty)
                ?: throw SnapLoopException.InvalidData("Match appearance is missing face identity metadata")
            if (it.faceProfileRevision.isBlank()) throw SnapLoopException.InvalidData("Match appearance is missing face profile revision")
            buildMap<String, Any> {
                put("participantUserId", it.participantUserId)
                put("confidence", it.confidence)
                put("faceIdentityId", identity)
                put("faceProfileRevision", it.faceProfileRevision)
                it.recipientMembershipId?.trim()?.takeIf(String::isNotEmpty)?.let { membership -> put("recipientMembershipId", membership) }
            }
        }
        val removals = match.recipientRemovals.map {
            if (it.participantUserId.isBlank() || it.faceIdentityId.isBlank() || it.faceProfileRevision.isBlank()) {
                throw SnapLoopException.InvalidData("Recipient removal is missing current identity metadata")
            }
            buildMap<String, Any> {
                put("participantUserId", it.participantUserId)
                put("faceIdentityId", it.faceIdentityId)
                put("faceProfileRevision", it.faceProfileRevision)
                it.recipientMembershipId?.trim()?.takeIf(String::isNotEmpty)?.let { membership -> put("recipientMembershipId", membership) }
            }
        }
        val metadataOnly = match.isSourceScopedIdentity && appearances.isEmpty() && removals.isNotEmpty()
        if (!metadataOnly) {
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            ref.putBytes(thumbnailJpeg, metadata).await()
        }
        val payload = buildMap<String, Any> {
            put("id", match.id)
            put("eventId", match.eventId)
            put("assetLocalId", match.assetLocalId)
            put("appearances", appearances)
            put("recipientRemovals", removals)
            put("capturedAtMillis", match.capturedAtMillis)
            put("matchedAtMillis", match.matchedAtMillis)
            put("thumbnailPath", path)
            put("mergeAppearances", match.isSourceScopedIdentity)
            put("metadataOnly", metadataOnly)
            match.sourceInstallationId?.trim()?.takeIf(String::isNotEmpty)?.let { put("sourceInstallationId", it) }
            match.sourceMembershipId?.trim()?.takeIf(String::isNotEmpty)?.let { put("sourceMembershipId", it) }
        }
        try {
            callable.call("publishMatch", payload)
        } catch (t: Throwable) {
            // Source-scoped IDs are incrementally merged and may already serve a previous recipient.
            if (!metadataOnly && !match.isSourceScopedIdentity) runCatching { ref.delete().await() }
            throw t
        }
    }

    suspend fun dismissAppearance(matchId: String, participantUserId: String) {
        val eventId = matchId.substringBefore(':', missingDelimiterValue = "")
        if (eventId.isBlank()) throw SnapLoopException.InvalidData("Match id is missing event id")
        callable.call("dismissAppearance", mapOf("eventId" to eventId, "matchId" to matchId, "participantUserId" to participantUserId))
    }

    suspend fun myPhotos(eventId: String, userId: String): List<PhotoMatch> {
        if (auth.currentUser?.uid != userId) throw SnapLoopException.NotAuthenticated()
        return matchedPhotos(eventId)
    }

    suspend fun sharedAlbum(eventId: String): List<PhotoMatch> {
        if (auth.currentUser == null) throw SnapLoopException.NotAuthenticated()
        return matchedPhotos(eventId)
    }

    suspend fun signedOriginalUrl(@Suppress("UNUSED_PARAMETER") match: PhotoMatch): Nothing =
        throw SnapLoopException.InvalidData("The original photo is not available in this SnapLoop release")

    private suspend fun matchedPhotos(eventId: String): List<PhotoMatch> {
        val raw = callable.call("listMyMatchedPhotos", mapOf("eventId" to eventId))
        val wrapper = raw as? Map<*, *> ?: throw SnapLoopException.InvalidData("Matched photo response is malformed")
        val rows = wrapper["photos"] as? List<*> ?: throw SnapLoopException.InvalidData("Matched photo response is malformed")
        return rows.map { decode(it as? Map<*, *> ?: throw SnapLoopException.InvalidData("Malformed photo")) }
            .sortedByDescending { it.capturedAtMillis }
    }

    private fun decode(data: Map<*, *>): PhotoMatch {
        val id = requiredString(data, "id")
        val eventId = requiredString(data, "eventId")
        val sourceUserId = requiredString(data, "sourceUserId")
        val assetLocalId = requiredString(data, "assetLocalId")
        val matchedMembershipIds = data["matchedMembershipIds"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val appearances = (data["appearances"] as? List<*>).orEmpty().mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            val userId = row["participantUserId"] as? String ?: return@mapNotNull null
            val identity = normalized(row["faceIdentityId"]) ?: return@mapNotNull null
            PhotoMatchAppearance(
                participantUserId = userId,
                recipientMembershipId = normalized(row["recipientMembershipId"]) ?: normalized(matchedMembershipIds[userId]),
                confidence = (row["confidence"] as? Number)?.toDouble() ?: 0.0,
                faceIdentityId = identity,
                faceProfileRevision = row["faceProfileRevision"] as? String ?: "",
                dismissedByUser = row["dismissedByUser"] as? Boolean ?: false,
            )
        }
        return PhotoMatch(
            id = id,
            eventId = eventId,
            ownerUserId = sourceUserId,
            sourceInstallationId = normalized(data["sourceInstallationId"]),
            sourceMembershipId = normalized(data["sourceMembershipId"]),
            assetLocalId = assetLocalId,
            appearances = appearances,
            capturedAtMillis = (data["capturedAtMillis"] as? Number)?.toLong() ?: throw SnapLoopException.InvalidData("Matched photo is missing capture date"),
            matchedAtMillis = (data["matchedAtMillis"] as? Number)?.toLong() ?: throw SnapLoopException.InvalidData("Matched photo is missing match date"),
            thumbnailPath = data["thumbnailPath"] as? String,
        )
    }

    private fun documentId(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun requiredString(data: Map<*, *>, key: String) = normalized(data[key]) ?: throw SnapLoopException.InvalidData("Matched photo is missing $key")
    private fun normalized(value: Any?): String? = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
