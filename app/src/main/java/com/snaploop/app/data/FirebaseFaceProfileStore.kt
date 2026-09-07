package com.snaploop.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.core.awaitResult
import com.snaploop.app.domain.BiometricConsentRecord
import com.snaploop.app.domain.FaceProfile
import com.snaploop.app.domain.FaceProfileReplacementPolicy
import com.snaploop.app.domain.FaceTemplatePose
import com.snaploop.app.domain.FaceTemplateRecord

/** users/{uid}/faceProfile/current; all writes remain server-authoritative. */
class FirebaseFaceProfileStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val callable: FirebaseCallableClient = FirebaseCallableClient(),
) {
    suspend fun load(userId: String): FaceProfile? {
        var snapshot = ref(userId).get().awaitResult()
        var data = snapshot.data ?: return null
        if (requiresStableIdentityMigration(data)) {
            callable.call("ensureMyFaceIdentity", emptyMap<String, Any>())
            snapshot = ref(userId).get().awaitResult()
            data = snapshot.data ?: return null
        }
        if (!isCurrentEligibleProfile(data)) return null
        return decodeProfile(userId, data)
    }

    suspend fun save(profile: FaceProfile) {
        require(profile.version == FaceModelPolicy.CURRENT_VERSION)
        require(profile.templates.size in 3..FaceModelPolicy.TARGET_TEMPLATE_COUNT)
        val existing = load(profile.userId)
        if (existing != null && existing.faceProfileRevision.isNotBlank() &&
            existing.faceProfileRevision != profile.faceProfileRevision &&
            !FaceProfileReplacementPolicy.isSameIdentity(profile, existing)) {
            throw SnapLoopException.InvalidData("This Face Setup does not appear to be the same person")
        }
        val templates = profile.templates.map {
            mapOf(
                "id" to it.id,
                "embedding" to it.embedding.map(Float::toDouble),
                "pose" to it.pose.wireValue,
                "quality" to it.quality,
                "createdAtMillis" to it.createdAtMillis,
            )
        }
        callable.call("saveMyFaceProfile", mapOf(
            "userId" to profile.userId,
            "embedding" to profile.embedding.map(Float::toDouble),
            "templates" to templates,
            "version" to profile.version,
            "updatedAtMillis" to profile.updatedAtMillis,
        ))

        // iOS explicitly refreshes the authenticated user's Event-scoped face roster after
        // enrollment/update. Android must do the same so iPhone participants scanning an Event
        // immediately receive the new Android templates instead of matching against a stale or
        // missing profile. Keep this server-authoritative; no biometric data is logged locally.
        callable.call("refreshMyFaceProfile", emptyMap<String, Any>())
    }

    suspend fun delete(userId: String) {
        callable.call("eraseMyFaceProfile", mapOf("userId" to userId))
    }

    private fun ref(userId: String) = db.collection("users").document(userId).collection("faceProfile").document("current")

    private fun requiresStableIdentityMigration(data: Map<String, Any>): Boolean {
        if (!normalized(data["faceIdentityId"]).isNullOrEmpty()) return false
        return (data["version"] as? Number)?.toInt() == FaceModelPolicy.CURRENT_VERSION &&
            (data["consentPolicyVersion"] as? Number)?.toInt() == BiometricConsentRecord.CURRENT_POLICY_VERSION &&
            data["consentDisclosureId"] == BiometricConsentRecord.CURRENT_DISCLOSURE_ID &&
            data["consentDisclosureSHA256"] == BiometricConsentRecord.CURRENT_DISCLOSURE_SHA256 &&
            ((data["expiresAt"] as? Timestamp)?.toDate()?.time ?: Long.MIN_VALUE) > System.currentTimeMillis()
    }

    private fun isCurrentEligibleProfile(data: Map<String, Any>): Boolean =
        (data["version"] as? Number)?.toInt() == FaceModelPolicy.CURRENT_VERSION &&
            (data["consentPolicyVersion"] as? Number)?.toInt() == BiometricConsentRecord.CURRENT_POLICY_VERSION &&
            data["consentDisclosureId"] == BiometricConsentRecord.CURRENT_DISCLOSURE_ID &&
            data["consentDisclosureSHA256"] == BiometricConsentRecord.CURRENT_DISCLOSURE_SHA256 &&
            !normalized(data["faceIdentityId"]).isNullOrEmpty() &&
            ((data["expiresAt"] as? Timestamp)?.toDate()?.time ?: Long.MIN_VALUE) > System.currentTimeMillis()

    private fun decodeProfile(userId: String, data: Map<String, Any>): FaceProfile {
        val embedding = Embeddings.normalize(numberVector(data["embedding"]))
            ?: throw SnapLoopException.InvalidData("Face profile embedding is invalid")
        val identityId = normalized(data["faceIdentityId"])
            ?: throw SnapLoopException.InvalidData("Face profile identity is missing")
        val version = (data["version"] as? Number)?.toInt() ?: 0
        val updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time
            ?: throw SnapLoopException.InvalidData("Face profile updatedAt is missing")
        val templates = (data["templates"] as? List<*>).orEmpty().mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            val pose = (row["pose"] as? String)?.let(FaceTemplatePose::fromWire) ?: return@mapNotNull null
            val vector = Embeddings.normalize(numberVector(row["embedding"])) ?: return@mapNotNull null
            FaceTemplateRecord(
                id = (row["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                embedding = vector,
                pose = pose,
                quality = (row["quality"] as? Number)?.toDouble() ?: 1.0,
                createdAtMillis = (row["createdAt"] as? Timestamp)?.toDate()?.time ?: updatedAt,
            )
        }
        if (version == FaceModelPolicy.CURRENT_VERSION && templates.size !in 3..FaceModelPolicy.TARGET_TEMPLATE_COUNT) {
            throw SnapLoopException.InvalidData("Face profile template count is invalid")
        }
        return FaceProfile(userId, identityId, embedding, templates, version, updatedAt)
    }

    private fun numberVector(value: Any?): FloatArray = when (value) {
        is List<*> -> value.mapNotNull { (it as? Number)?.toFloat() }.toFloatArray()
        is FloatArray -> value
        else -> floatArrayOf()
    }
    private fun normalized(value: Any?): String? = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
}