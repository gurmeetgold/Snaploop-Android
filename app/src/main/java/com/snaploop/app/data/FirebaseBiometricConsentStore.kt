package com.snaploop.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.snaploop.app.BuildConfig
import com.snaploop.app.core.FirebaseErrorMapper
import com.snaploop.app.core.awaitResult
import com.snaploop.app.domain.BiometricConsentRecord
import java.util.Locale

/** Firestore path users/{uid}/privacy/biometricConsent; trusted mutations use v5 callables. */
class FirebaseBiometricConsentStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val callable: FirebaseCallableClient = FirebaseCallableClient(),
) {
    suspend fun load(userId: String): BiometricConsentRecord? {
        return try {
            val data = db.collection("users").document(userId).collection("privacy").document("biometricConsent")
                .get().awaitResult().data ?: return null
            BiometricConsentRecord(
                userId = userId,
                policyVersion = (data["policyVersion"] as? Number)?.toInt() ?: 0,
                disclosureId = data["disclosureId"] as? String ?: "",
                disclosureSha256 = data["disclosureSHA256"] as? String ?: "",
                acceptedAtMillis = (data["acceptedAt"] as? Timestamp)?.toDate()?.time ?: Long.MIN_VALUE,
                withdrawnAtMillis = (data["withdrawnAt"] as? Timestamp)?.toDate()?.time,
                expiredAtMillis = (data["expiredAt"] as? Timestamp)?.toDate()?.time,
                expiresAtMillis = (data["expiresAt"] as? Timestamp)?.toDate()?.time,
                jurisdictionCountry = data["jurisdictionCountry"] as? String ?: "",
                jurisdictionSubdivision = data["jurisdictionSubdivision"] as? String ?: "",
                appVersion = data["appVersion"] as? String ?: "unknown",
                platform = data["platform"] as? String ?: "Android",
                locale = data["locale"] as? String ?: "unknown",
                acceptedVia = data["acceptedVia"] as? String ?: "unknown",
                age18Attested = data["age18Attested"] as? Boolean ?: false,
                noticeAcknowledged = data["noticeAcknowledged"] as? Boolean ?: false,
                ownFaceAttested = data["ownFaceAttested"] as? Boolean ?: false,
                lastBiometricActivityAtMillis = (data["lastBiometricActivityAt"] as? Timestamp)?.toDate()?.time,
            )
        } catch (t: Throwable) {
            throw FirebaseErrorMapper.firestore(t)
        }
    }

    suspend fun accept(userId: String, country: String, subdivision: String, age18: Boolean, notice: Boolean, ownFace: Boolean) {
        callable.call("acceptBiometricConsent", mapOf(
            "userId" to userId,
            "policyVersion" to BiometricConsentRecord.CURRENT_POLICY_VERSION,
            "disclosureId" to BiometricConsentRecord.CURRENT_DISCLOSURE_ID,
            "disclosureSHA256" to BiometricConsentRecord.CURRENT_DISCLOSURE_SHA256,
            "jurisdictionCountry" to country.uppercase(Locale.US),
            "jurisdictionSubdivision" to subdivision.uppercase(Locale.US),
            "appVersion" to BuildConfig.VERSION_NAME,
            "platform" to BiometricConsentRecord.PLATFORM,
            "locale" to Locale.getDefault().toLanguageTag(),
            "acceptedVia" to BiometricConsentRecord.CONSENT_METHOD,
            "age18Attested" to age18,
            "noticeAcknowledged" to notice,
            "ownFaceAttested" to ownFace,
        ))
    }

    suspend fun withdraw(userId: String) { callable.call("withdrawBiometricConsent", mapOf("userId" to userId)) }
}
