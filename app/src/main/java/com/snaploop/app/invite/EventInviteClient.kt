package com.snaploop.app.invite

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class EventInviteDelivery(
    val kind: Kind,
    val phoneNumber: String,
) {
    enum class Kind { IN_APP, SMS }
}

data class EventInviteStatusRow(
    val phoneNumber: String,
    val status: String,
    val delivery: String,
)

/** Firebase callable contract shared with the pinned iOS EventInviteClient. */
class EventInviteClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    suspend fun invite(eventId: String, phoneNumber: String): EventInviteDelivery {
        val result = functions.getHttpsCallable("inviteByPhone")
            .call(mapOf("eventId" to eventId, "phoneNumber" to phoneNumber))
            .await()
        val data = result.data as? Map<*, *>
            ?: error("Invite service returned an invalid response.")
        val deliveryRaw = data["delivery"] as? String
            ?: error("Invite service returned an invalid response.")
        val kind = when (deliveryRaw) {
            "in_app" -> EventInviteDelivery.Kind.IN_APP
            "sms" -> EventInviteDelivery.Kind.SMS
            else -> error("Invite service returned an invalid response.")
        }
        return EventInviteDelivery(
            kind = kind,
            phoneNumber = (data["phoneNumber"] as? String) ?: phoneNumber,
        )
    }

    suspend fun list(eventId: String): List<EventInviteStatusRow> {
        val result = functions.getHttpsCallable("listEventInvites")
            .call(mapOf("eventId" to eventId))
            .await()
        val data = result.data as? Map<*, *> ?: return emptyList()
        val rows = data["invites"] as? List<*> ?: return emptyList()
        return rows.mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            EventInviteStatusRow(
                phoneNumber = (row["phoneNumber"] as? String).orEmpty(),
                status = (row["status"] as? String) ?: "invited",
                delivery = (row["delivery"] as? String) ?: "sms",
            )
        }
    }

    companion object {
        fun userMessage(error: Throwable): String {
            val text = error.localizedMessage ?: "The invitation could not be sent."
            return if (text.uppercase().contains("NOT FOUND")) {
                "SnapLoop's event service needs to be updated. Deploy the latest Firebase Functions, then try again."
            } else {
                text
            }
        }
    }
}
