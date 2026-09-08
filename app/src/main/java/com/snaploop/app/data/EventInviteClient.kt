package com.snaploop.app.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Server-authoritative invitation operations mirrored from the pinned iOS EventInviteClient. */
class EventInviteClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    data class Preview(
        val eventId: String,
        val inviterName: String?,
    )

    suspend fun previewToken(token: String): Preview = preview(kind = "e", value = token)

    suspend fun previewCode(code: String): Preview = preview(kind = "c", value = code)

    suspend fun decline(eventId: String) {
        functions.getHttpsCallable("declineEventInvite")
            .call(mapOf("eventId" to eventId))
            .await()
    }

    private suspend fun preview(kind: String, value: String): Preview {
        val raw = functions.getHttpsCallable("resolveInvitePreview")
            .call(mapOf("kind" to kind, "value" to value))
            .await()
            .data
        val data = raw as? Map<*, *> ?: error("Invite preview returned malformed data")
        val eventId = (data["eventId"] as? String)?.trim().orEmpty()
        require(eventId.isNotEmpty()) { "Invite preview is missing the Event." }
        val inviter = (data["inviterName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return Preview(eventId = eventId, inviterName = inviter)
    }
}
