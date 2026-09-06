package com.snaploop.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.snaploop.app.model.BiometricConsentRecord
import kotlinx.coroutines.tasks.await
import java.time.Instant

class FirebaseBiometricConsentStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    suspend fun load(userId: String): BiometricConsentRecord? {
        val data = db.collection("users").document(userId).collection("privacy")
            .document("biometricConsent").get().await().data ?: return null
        fun ts(key: String): Instant? = (data[key] as? Timestamp)?.toDate()?.toInstant()
        return BiometricConsentRecord(
            userId = userId,
            policyVersion = (data["policyVersion"] as? Number)?.toInt() ?: 0,
            disclosureId = data["disclosureId"] as? String ?: "",
            disclosureSha256 = data["disclosureSHA256"] as? String ?: "",
            acceptedAt = ts("acceptedAt") ?: Instant.EPOCH,
            withdrawnAt = ts("withdrawnAt"),
            expiredAt = ts("expiredAt"),
            expiresAt = ts("expiresAt"),
            jurisdictionCountry = data["jurisdictionCountry"] as? String ?: "",
            jurisdictionSubdivision = data["jurisdictionSubdivision"] as? String ?: "",
            appVersion = data["appVersion"] as? String ?: "unknown",
            platform = data["platform"] as? String ?: "Android",
            locale = data["locale"] as? String ?: "unknown",
            acceptedVia = data["acceptedVia"] as? String ?: "unknown",
            age18Attested = data["age18Attested"] as? Boolean ?: false,
            noticeAcknowledged = data["noticeAcknowledged"] as? Boolean ?: false,
            ownFaceAttested = data["ownFaceAttested"] as? Boolean ?: false,
            lastBiometricActivityAt = ts("lastBiometricActivityAt")
        )
    }

    suspend fun save(record: BiometricConsentRecord) {
        require(record.userId.isNotBlank())
        require(record.policyVersion == BiometricConsentRecord.CURRENT_POLICY_VERSION)
        require(record.disclosureId == BiometricConsentRecord.CURRENT_DISCLOSURE_ID)
        require(record.disclosureSha256 == BiometricConsentRecord.CURRENT_DISCLOSURE_SHA256)
        functions.getHttpsCallable("acceptBiometricConsent").call(mapOf(
            "userId" to record.userId,
            "policyVersion" to record.policyVersion,
            "disclosureId" to record.disclosureId,
            "disclosureSHA256" to record.disclosureSha256,
            "jurisdictionCountry" to record.jurisdictionCountry.uppercase(),
            "jurisdictionSubdivision" to record.jurisdictionSubdivision.uppercase(),
            "appVersion" to record.appVersion,
            "platform" to "Android",
            "locale" to record.locale,
            "acceptedVia" to record.acceptedVia,
            "age18Attested" to record.age18Attested,
            "noticeAcknowledged" to record.noticeAcknowledged,
            "ownFaceAttested" to record.ownFaceAttested
        )).await()
    }

    suspend fun withdraw(userId: String) {
        functions.getHttpsCallable("withdrawBiometricConsent").call(mapOf("userId" to userId)).await()
    }
}
