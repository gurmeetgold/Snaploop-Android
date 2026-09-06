package com.snaploop.app.domain

import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.FaceParticipant
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Wire values intentionally match the production iOS client and backend. */
enum class EventCategory(val wireValue: String) {
    TRIP("trip"), WEDDING("wedding"), PARTY("party"), BIRTHDAY("birthday"),
    CONFERENCE("conference"), FAMILY("family"), SPORTS("sports"), OTHER("other");

    companion object { fun fromWire(value: String): EventCategory? = entries.firstOrNull { it.wireValue == value } }
}

enum class EventStatus(val wireValue: String) {
    ACTIVE("active"), ENDED_BY_ORGANIZER("endedByOrganizer"), DELETED_BY_ORGANIZER("deletedByOrganizer"), EXPIRED("expired");
    companion object { fun fromWire(value: String): EventStatus? = entries.firstOrNull { it.wireValue == value } }
}

data class Event(
    val id: String,
    val joinCode: String,
    val inviteToken: String,
    val creatorUserId: String,
    val name: String,
    val category: EventCategory,
    val coverImagePath: String?,
    val locationName: String?,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val photoWindowVersion: Int?,
    val photoWindowTimeZoneId: String?,
    val photoWindowStartDayNumber: Int?,
    val photoWindowEndDayNumber: Int?,
    val status: EventStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    init { require(id.isNotBlank()); require(joinCode.isNotBlank()); require(endsAtMillis >= startsAtMillis) }

    val usesCanonicalPhotoWindow: Boolean
        get() = photoWindowVersion == CANONICAL_PHOTO_WINDOW_VERSION &&
            !photoWindowTimeZoneId.isNullOrBlank() &&
            runCatching { ZoneId.of(photoWindowTimeZoneId) }.isSuccess

    val photoWindowRevision: String
        get() = if (usesCanonicalPhotoWindow && photoWindowStartDayNumber != null && photoWindowEndDayNumber != null) {
            "v$CANONICAL_PHOTO_WINDOW_VERSION:$photoWindowTimeZoneId:$photoWindowStartDayNumber-$photoWindowEndDayNumber"
        } else "legacy:${startsAtMillis / 1000.0}-${endsAtMillis / 1000.0}"

    companion object { const val CANONICAL_PHOTO_WINDOW_VERSION = 1 }
}

data class EventMember(
    val userId: String,
    val membershipId: String?,
    val displayName: String?,
    val role: Role,
    val joinedAtMillis: Long,
    val sharingEnabled: Boolean,
    val lastSyncAtMillis: Long?,
    val faceTemplateVersion: Int,
) {
    enum class Role(val wireValue: String) {
        ORGANIZER("organizer"), ADMIN("admin"), PARTICIPANT("participant");
        val canManageMembers get() = this == ORGANIZER || this == ADMIN
        companion object { fun fromWire(value: String): Role? = entries.firstOrNull { it.wireValue == value } }
    }
}

enum class FaceTemplatePose(val wireValue: String) {
    CENTER("center"), SIDE_A("sideA"), SIDE_B("sideB"), TILTED("tilted"), ALTERNATE("alternate"), IMPORTED("imported");
    companion object { fun fromWire(value: String): FaceTemplatePose? = entries.firstOrNull { it.wireValue == value } }
}

data class FaceTemplateRecord(
    val id: String,
    val embedding: FloatArray,
    val pose: FaceTemplatePose,
    val quality: Double,
    val createdAtMillis: Long,
)

data class EventParticipant(
    val userId: String,
    val membershipId: String?,
    val displayName: String?,
    val faceIdentityId: String?,
    val faceEmbedding: FloatArray,
    val faceTemplates: List<FaceTemplateRecord>,
    val faceProfileVersion: Int,
    val joinedAtMillis: Long,
) {
    val faceProfileRevision: String
        get() {
            val ids = faceTemplates.map { it.id }.filter { it.isNotBlank() }.sorted()
            return if (ids.isEmpty()) "" else "v$faceProfileVersion:${ids.joinToString("|")}" 
        }

    fun effectiveEmbeddings(): List<FloatArray> {
        if (faceTemplates.isEmpty()) return listOf(faceEmbedding)
        val onePerPose = FaceTemplatePose.entries.mapNotNull { pose ->
            faceTemplates.filter { it.pose == pose && it.quality.isFinite() }
                .maxWithOrNull(compareBy<FaceTemplateRecord> { it.quality }.thenBy { it.createdAtMillis })?.embedding
        }
        return onePerPose.ifEmpty { listOf(faceEmbedding) }
    }

    fun asMatcherParticipant(): FaceParticipant = FaceParticipant(
        userId = userId,
        membershipId = membershipId,
        faceIdentityId = faceIdentityId.orEmpty().trim(),
        faceProfileRevision = faceProfileRevision,
        faceProfileVersion = faceProfileVersion,
        embeddings = effectiveEmbeddings(),
    )
}

data class SnapUser(
    val id: String,
    val phoneNumber: String,
    val displayName: String?,
    val hasFaceProfile: Boolean,
    val createdAtMillis: Long,
)

data class BiometricJurisdiction(val countryCode: String, val subdivisionCode: String = "") {
    val normalizedCountry = countryCode.uppercase(Locale.US)
    val normalizedSubdivision = subdivisionCode.uppercase(Locale.US)
    val isFaceMatchAvailable: Boolean
        get() = when (normalizedCountry) {
            "IN" -> normalizedSubdivision.isEmpty()
            "CA" -> normalizedSubdivision in CANADIAN_SUBDIVISIONS
            else -> false
        }

    companion object {
        val CANADIAN_SUBDIVISIONS = setOf("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "SK", "YT")
    }
}

data class BiometricConsentRecord(
    val userId: String,
    val policyVersion: Int,
    val disclosureId: String,
    val disclosureSha256: String,
    val acceptedAtMillis: Long,
    val withdrawnAtMillis: Long?,
    val expiredAtMillis: Long?,
    val expiresAtMillis: Long?,
    val jurisdictionCountry: String,
    val jurisdictionSubdivision: String,
    val appVersion: String,
    val platform: String,
    val locale: String,
    val acceptedVia: String,
    val age18Attested: Boolean,
    val noticeAcknowledged: Boolean,
    val ownFaceAttested: Boolean,
    val lastBiometricActivityAtMillis: Long?,
) {
    val jurisdiction get() = BiometricJurisdiction(jurisdictionCountry, jurisdictionSubdivision)

    fun isActive(nowMillis: Long = Instant.now().toEpochMilli()): Boolean =
        policyVersion == CURRENT_POLICY_VERSION &&
            disclosureId == CURRENT_DISCLOSURE_ID &&
            disclosureSha256 == CURRENT_DISCLOSURE_SHA256 &&
            withdrawnAtMillis == null && expiredAtMillis == null &&
            (expiresAtMillis?.let { it > nowMillis } == true) &&
            age18Attested && noticeAcknowledged && ownFaceAttested && jurisdiction.isFaceMatchAvailable

    companion object {
        const val CURRENT_POLICY_VERSION = 5
        const val CURRENT_DISCLOSURE_ID = "biometric-consent-v5"
        const val CURRENT_DISCLOSURE_SHA256 = "2b78a5de4ced7219953cf4c3b62e07dce41392b0090f7c07c3fcb307411bc30f"
        const val CONSENT_METHOD = "explicit-button"
        const val PLATFORM = "Android"
    }
}
