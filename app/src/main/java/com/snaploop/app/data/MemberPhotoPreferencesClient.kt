package com.snaploop.app.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class MemberPhotoPreferences(
    val sharingEnabled: Boolean,
    val includeOwnMatches: Boolean,
    val revisionToken: String,
)

/** Mirrors the current iOS member photo-preference callables. */
class MemberPhotoPreferencesClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    suspend fun load(eventId: String): MemberPhotoPreferences {
        val raw = functions.getHttpsCallable("getMemberPhotoPreferences")
            .call(mapOf("eventId" to eventId))
            .await()
            .data
        val data = raw as? Map<*, *> ?: error("Photo preferences returned malformed data")

        val sharingUpdated = millisString(data["sharingUpdatedAtMillis"])
        val ownUpdated = millisString(data["ownMatchesUpdatedAtMillis"])
        val sharingRevision = normalizedString(data["sharingRevision"])
            ?.let { "id:$it" }
            ?: "legacy:$sharingUpdated"
        val ownRevision = normalizedString(data["ownMatchesRevision"])
            ?.let { "id:$it" }
            ?: "legacy:$ownUpdated"

        return MemberPhotoPreferences(
            sharingEnabled = data["sharingEnabled"] as? Boolean ?: true,
            includeOwnMatches = data["includeOwnMatches"] as? Boolean ?: false,
            revisionToken = "share=$sharingRevision;own=$ownRevision",
        )
    }

    suspend fun setIncludeOwnMatches(eventId: String, enabled: Boolean) {
        functions.getHttpsCallable("setOwnPhotoVisibility")
            .call(mapOf("eventId" to eventId, "enabled" to enabled))
            .await()
    }

    suspend fun disableOwnMatchesEverywhere() {
        functions.getHttpsCallable("disableOwnMatchesEverywhere").call(emptyMap<String, Any>()).await()
    }

    private fun normalizedString(value: Any?): String? =
        (value as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun millisString(value: Any?): String = when (value) {
        is Number -> value.toLong().toString()
        else -> "0"
    }
}
