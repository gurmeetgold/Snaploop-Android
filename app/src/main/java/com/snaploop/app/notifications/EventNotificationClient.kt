package com.snaploop.app.notifications

import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await

data class EventNotification(
    val id: String,
    val eventId: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val type: String? = null,
    val eventName: String? = null,
    val inviteToken: String? = null,
    val read: Boolean = false,
)

/** Pure mapping/list policy mirrored from pinned iOS EventNotificationClient + HomeModel. */
object EventNotificationPolicy {
    fun fromRecord(
        documentId: String,
        eventId: String?,
        title: String?,
        body: String?,
        createdAt: Instant?,
        type: String? = null,
        eventName: String? = null,
        inviteToken: String? = null,
        read: Boolean? = null,
    ): EventNotification? {
        if (documentId.isBlank()) return null
        val cleanEventId = eventId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cleanTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val cleanBody = body?.takeIf { it.isNotBlank() } ?: return null
        return EventNotification(
            id = documentId,
            eventId = cleanEventId,
            title = cleanTitle,
            body = cleanBody,
            createdAt = createdAt ?: Instant.MIN,
            type = type?.trim()?.takeIf { it.isNotEmpty() },
            eventName = eventName?.trim()?.takeIf { it.isNotEmpty() },
            inviteToken = inviteToken?.trim()?.takeIf { it.isNotEmpty() },
            read = read ?: false,
        )
    }

    fun newestFirst(notifications: List<EventNotification>): List<EventNotification> =
        notifications.sortedByDescending(EventNotification::createdAt)

    fun withoutEvent(
        notifications: List<EventNotification>,
        eventId: String,
    ): List<EventNotification> {
        val target = eventId.trim()
        if (target.isEmpty()) return notifications
        return notifications.filterNot { it.eventId == target }
    }
}

/** Firebase-backed unread update contract pinned to users/{uid}/notifications. */
class EventNotificationClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun unread(userId: String): List<EventNotification> {
        require(userId.isNotBlank()) { "A signed-in user is required." }
        val snapshot = firestore
            .collection("users")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("read", false)
            .limit(20)
            .get()
            .await()

        val notifications = snapshot.documents.mapNotNull { document ->
            EventNotificationPolicy.fromRecord(
                documentId = document.id,
                eventId = document.getString("eventId"),
                title = document.getString("title"),
                body = document.getString("body"),
                createdAt = document.getTimestamp("createdAt")?.toDate()?.toInstant(),
                type = document.getString("type"),
                eventName = document.getString("eventName"),
                inviteToken = document.getString("inviteToken"),
                read = document.getBoolean("read"),
            )
        }
        return EventNotificationPolicy.newestFirst(notifications)
    }

    suspend fun markRead(userId: String, notificationId: String) {
        require(userId.isNotBlank()) { "A signed-in user is required." }
        require(notificationId.isNotBlank()) { "A notification is required." }
        firestore
            .collection("users")
            .document(userId)
            .collection("notifications")
            .document(notificationId)
            .update("read", true)
            .await()
    }
}

/**
 * In-process invalidation mirrors iOS HomeModel.invitationDeclined(eventId:).
 * No notification content is persisted; screens that were not active simply
 * reload the server-authoritative unread collection when they compose/resume.
 */
object EventNotificationInvalidationStore {
    private val _eventIds = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val eventIds: SharedFlow<String> = _eventIds.asSharedFlow()

    fun invalidate(eventId: String) {
        val clean = eventId.trim().takeIf { it.isNotEmpty() } ?: return
        _eventIds.tryEmit(clean)
    }
}
