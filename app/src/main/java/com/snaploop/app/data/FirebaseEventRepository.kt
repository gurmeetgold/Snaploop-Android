package com.snaploop.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Android event repository mirrored against the production iOS Firebase contract.
 *
 * Important schema detail:
 * - callable Cloud Functions exchange epoch-millis fields (startsAtMillis, ...)
 * - persisted Firestore event documents store Timestamp fields (startsAt, ...)
 *
 * Decoding accepts both representations so callable previews and direct Firestore
 * reads share one model without silently dropping otherwise valid Events.
 */
class FirebaseEventRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    suspend fun createEvent(event: SnapEvent) {
        val payload = mutableMapOf<String, Any?>(
            "id" to event.id,
            "joinCode" to event.joinCode,
            "inviteToken" to event.inviteToken,
            "creatorUserId" to event.creatorUserId,
            "name" to event.name,
            "category" to event.category.name,
            "status" to event.status.name,
            "startsAtMillis" to event.startsAt.toEpochMilli(),
            "endsAtMillis" to event.endsAt.toEpochMilli(),
            "startsAtOffsetMinutes" to offsetMinutes(event.startsAt, event.photoWindowTimeZoneId),
            "endsAtOffsetMinutes" to offsetMinutes(event.endsAt, event.photoWindowTimeZoneId),
            "nowOffsetMinutes" to offsetMinutes(Instant.now(), event.photoWindowTimeZoneId),
            "createdAtMillis" to event.createdAt.toEpochMilli(),
            "updatedAtMillis" to event.updatedAt.toEpochMilli(),
            "coverImagePath" to event.coverImagePath,
            "locationName" to event.locationName
        )
        event.photoWindowVersion?.let { payload["photoWindowVersion"] = it }
        event.photoWindowTimeZoneId?.takeIf { it.isNotBlank() }?.let { payload["photoWindowTimeZoneId"] = it }
        event.photoWindowStartDayNumber?.let { payload["photoWindowStartDayNumber"] = it }
        event.photoWindowEndDayNumber?.let { payload["photoWindowEndDayNumber"] = it }
        functions.getHttpsCallable("createEvent").call(payload).await()
    }

    suspend fun fetchEvent(id: String): SnapEvent {
        val doc = db.collection("events").document(id).get().await()
        check(doc.exists()) { "Event not found" }
        return decodeEvent(doc.id, doc.data.orEmpty())
    }

    suspend fun resolveJoinCode(joinCode: String): SnapEvent = resolveInvite(mapOf("joinCode" to joinCode))
    suspend fun resolveInviteToken(inviteToken: String): SnapEvent = resolveInvite(mapOf("inviteToken" to inviteToken))

    suspend fun join(eventId: String) {
        functions.getHttpsCallable("joinEvent").call(mapOf("eventId" to eventId)).await()
    }

    suspend fun leave(eventId: String, userId: String) {
        functions.getHttpsCallable("leaveEvent").call(mapOf("eventId" to eventId, "userId" to userId)).await()
    }

    suspend fun setSharing(eventId: String, userId: String, enabled: Boolean) {
        functions.getHttpsCallable("setSharing").call(
            mapOf("eventId" to eventId, "userId" to userId, "enabled" to enabled)
        ).await()
    }

    suspend fun updateEvent(
        event: SnapEvent,
        includeDates: Boolean = true,
        expectedUpdatedAt: Instant = event.updatedAt,
    ) {
        functions.getHttpsCallable("updateEventManaged").call(
            managedEventUpdatePayload(
                event = event,
                includeDates = includeDates,
                expectedUpdatedAt = expectedUpdatedAt,
            )
        ).await()
    }

    suspend fun setStatus(eventId: String, status: EventStatus) {
        functions.getHttpsCallable("setEventStatus").call(
            mapOf("eventId" to eventId, "status" to status.name)
        ).await()
    }

    suspend fun setMemberRole(eventId: String, userId: String, role: EventMember.Role) {
        require(role == EventMember.Role.admin || role == EventMember.Role.participant)
        functions.getHttpsCallable("manageEventMember").call(
            mapOf(
                "eventId" to eventId,
                "userId" to userId,
                "action" to "setRole",
                "role" to role.name,
            )
        ).await()
    }

    suspend fun eventsForUser(userId: String): List<SnapEvent> {
        val refs = db.collection("users").document(userId).collection("eventRefs").get().await()
        val result = mutableListOf<SnapEvent>()
        for (ref in refs.documents) {
            val eventId = ref.getString("eventId") ?: ref.id
            try {
                val eventDoc = db.collection("events").document(eventId).get().await()
                if (eventDoc.exists()) {
                    // Do not hide decode/schema errors here. A previous getOrNull() caused
                    // successfully-created Events to vanish from Home when the Android
                    // decoder expected callable millis instead of Firestore Timestamps.
                    result += decodeEvent(eventDoc.id, eventDoc.data.orEmpty())
                }
            } catch (e: FirebaseFirestoreException) {
                // Stale membership refs can legitimately outlive access briefly after
                // leaving/deletion. Match the iOS behavior by skipping only those known
                // access/not-found cases; all other failures remain visible to callers.
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                    e.code != FirebaseFirestoreException.Code.NOT_FOUND
                ) {
                    throw e
                }
            }
        }
        return result.sortedByDescending { it.startsAt }
    }

    suspend fun members(eventId: String): List<EventMember> {
        val raw = functions.getHttpsCallable("listEventMembers").call(mapOf("eventId" to eventId)).await().data
        val wrapper = raw as? Map<*, *> ?: error("Malformed listEventMembers response")
        val rows = wrapper["members"] as? List<*> ?: error("Missing members")
        return rows.map { decodeMember(it as? Map<*, *> ?: error("Malformed member")) }.sortedBy { it.joinedAt }
    }

    private suspend fun resolveInvite(payload: Map<String, Any>): SnapEvent {
        val raw = functions.getHttpsCallable("resolveInvite").call(payload).await().data
        val wrapper = raw as? Map<*, *> ?: error("Malformed resolveInvite response")
        val eventMap = (wrapper["event"] as? Map<*, *>) ?: wrapper
        return decodeEvent(eventMap.string("id"), eventMap)
    }

    private fun decodeEvent(id: String, data: Map<*, *>): SnapEvent = SnapEvent(
        id = id,
        joinCode = data.string("joinCode"),
        inviteToken = data.stringOrNull("inviteToken").orEmpty(),
        creatorUserId = data.string("creatorUserId"),
        name = data.string("name"),
        category = enumValueOrDefault(data.stringOrNull("category"), EventCategory.other),
        coverImagePath = data.stringOrNull("coverImagePath"),
        locationName = data.stringOrNull("locationName"),
        startsAt = data.instant("startsAt", "startsAtMillis"),
        endsAt = data.instant("endsAt", "endsAtMillis"),
        photoWindowVersion = data.intOrNull("photoWindowVersion"),
        photoWindowTimeZoneId = data.stringOrNull("photoWindowTimeZoneId"),
        photoWindowStartDayNumber = data.intOrNull("photoWindowStartDayNumber"),
        photoWindowEndDayNumber = data.intOrNull("photoWindowEndDayNumber"),
        status = enumValueOrDefault(data.stringOrNull("status"), EventStatus.active),
        createdAt = data.instant("createdAt", "createdAtMillis"),
        updatedAt = data.instantOrNull("updatedAt", "updatedAtMillis")
            ?: data.instant("createdAt", "createdAtMillis")
    )

    private fun decodeMember(data: Map<*, *>): EventMember = EventMember(
        userId = data.string("userId"),
        membershipId = data.stringOrNull("membershipId"),
        displayName = data.stringOrNull("displayName"),
        role = enumValueOrDefault(data.stringOrNull("role"), EventMember.Role.participant),
        joinedAt = data.instant("joinedAt", "joinedAtMillis"),
        sharingEnabled = data["sharingEnabled"] as? Boolean ?: true,
        lastSyncAt = data.instantOrNull("lastSyncAt", "lastSyncAtMillis"),
        faceTemplateVersion = data.intOrNull("faceTemplateVersion") ?: 0
    )

    private fun offsetMinutes(instant: Instant, timeZoneId: String?): Int {
        val zone = runCatching { ZoneId.of(timeZoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
        return zone.rules.getOffset(instant).totalSeconds / 60
    }

    private fun Map<*, *>.instant(primaryKey: String, millisKey: String): Instant =
        instantOrNull(primaryKey, millisKey) ?: error("Missing $primaryKey/$millisKey")

    private fun Map<*, *>.instantOrNull(primaryKey: String, millisKey: String): Instant? {
        val value = this[primaryKey] ?: this[millisKey] ?: return null
        return when (value) {
            is Timestamp -> value.toDate().toInstant()
            is Date -> value.toInstant()
            is Instant -> value
            is Number -> Instant.ofEpochMilli(value.toLong())
            else -> null
        }
    }

    private fun Map<*, *>.string(key: String) = stringOrNull(key) ?: error("Missing $key")
    private fun Map<*, *>.stringOrNull(key: String) = (this[key] as? String)?.takeIf { it.isNotBlank() }
    private fun Map<*, *>.intOrNull(key: String) = (this[key] as? Number)?.toInt()
    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
