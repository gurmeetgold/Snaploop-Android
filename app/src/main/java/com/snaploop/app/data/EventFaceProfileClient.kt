package com.snaploop.app.data

import com.google.firebase.auth.FirebaseAuth
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.domain.EventParticipant
import com.snaploop.app.domain.FaceTemplatePose
import com.snaploop.app.domain.FaceTemplateRecord

/** Trusted biometric roster returned by listEventFaceProfiles. */
data class EventFaceProfileManifest(
    val participants: List<EventParticipant>,
    val sourceMembershipId: String?,
)

class EventFaceProfileClient(
    private val callable: FirebaseCallableClient = FirebaseCallableClient(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    suspend fun manifest(eventId: String): EventFaceProfileManifest {
        val raw = callable.call("listEventFaceProfiles", mapOf("eventId" to eventId))
        val wrapper = raw as? Map<*, *> ?: throw SnapLoopException.InvalidData("Event face profile response is malformed")
        val rows = wrapper["participants"] as? List<*> ?: throw SnapLoopException.InvalidData("Event face profile response is malformed")
        val sourceMembershipId = normalized(wrapper["callerMembershipId"])
        val hasLegacyRows = rows.any { normalized((it as? Map<*, *>)?.get("membershipId")) == null }

        // Production backend must return generation-bound membership IDs. Do not scan
        // if a stale deployment would make publication authorization ambiguous.
        if (sourceMembershipId == null || hasLegacyRows) {
            throw SnapLoopException.InvalidData("SnapLoop matching service needs to be updated before scanning")
        }

        return EventFaceProfileManifest(
            participants = rows.map { decode(it as? Map<*, *> ?: throw SnapLoopException.InvalidData("Malformed participant")) },
            sourceMembershipId = sourceMembershipId,
        )
    }

    suspend fun listIncludingSourceMembershipCarrier(eventId: String): List<EventParticipant> {
        val manifest = manifest(eventId)
        val uid = auth.currentUser?.uid ?: return manifest.participants
        if (manifest.participants.any { it.userId == uid }) return manifest.participants
        return manifest.participants + EventParticipant(
            userId = uid,
            membershipId = manifest.sourceMembershipId,
            displayName = null,
            faceIdentityId = null,
            faceEmbedding = floatArrayOf(1f),
            faceTemplates = emptyList(),
            faceProfileVersion = 0,
            joinedAtMillis = 0,
        )
    }

    private fun decode(data: Map<*, *>): EventParticipant {
        val userId = data["userId"] as? String ?: throw SnapLoopException.InvalidData("Event participant identity is missing")
        val membershipId = normalized(data["membershipId"])
            ?: throw SnapLoopException.InvalidData("Event participant membership generation is missing")
        val faceIdentityId = normalized(data["faceIdentityId"])
            ?: throw SnapLoopException.InvalidData("Event participant face identity is missing")
        val rawEmbedding = numberVector(data["faceEmbedding"])
        val embedding = Embeddings.normalize(rawEmbedding)
            ?: throw SnapLoopException.InvalidData("Event participant embedding is invalid")
        val joinedAt = (data["joinedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val templates = (data["faceTemplates"] as? List<*>).orEmpty().mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val pose = (row["pose"] as? String)?.let(FaceTemplatePose::fromWire) ?: return@mapNotNull null
            val normalizedEmbedding = Embeddings.normalize(numberVector(row["embedding"])) ?: return@mapNotNull null
            FaceTemplateRecord(
                id = (row["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                embedding = normalizedEmbedding,
                pose = pose,
                quality = (row["quality"] as? Number)?.toDouble() ?: 1.0,
                createdAtMillis = (row["createdAtMillis"] as? Number)?.toLong() ?: joinedAt,
            )
        }
        val version = (data["faceProfileVersion"] as? Number)?.toInt() ?: 0
        if (version == FaceModelPolicy.CURRENT_VERSION && templates.size !in 3..5) {
            throw SnapLoopException.InvalidData("Current face profile has an invalid template count")
        }
        return EventParticipant(
            userId = userId,
            membershipId = membershipId,
            displayName = (data["displayName"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            faceIdentityId = faceIdentityId,
            faceEmbedding = embedding,
            faceTemplates = templates,
            faceProfileVersion = version,
            joinedAtMillis = joinedAt,
        )
    }

    private fun numberVector(value: Any?): FloatArray = when (value) {
        is List<*> -> value.mapNotNull { (it as? Number)?.toFloat() }.toFloatArray()
        is FloatArray -> value
        else -> floatArrayOf()
    }

    private fun normalized(value: Any?): String? = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
