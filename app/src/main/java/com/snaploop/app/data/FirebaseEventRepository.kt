package com.snaploop.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import kotlinx.coroutines.tasks.await
import java.time.Instant

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
            "createdAtMillis" to event.createdAt.toEpochMilli(),
            "updatedAtMillis" to event.updatedAt.toEpochMilli(),
            "coverImagePath" to event.coverImagePath,
            "locationName" to event.locationName
        )
        event.photoWindowVersion?.let { payload["photoWindowVersion"] = it }
        event.photoWindowTimeZoneId?.let { payload["photoWindowTimeZoneId"] = it }
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
        functions.getHttpsCallable("setSharing").call(mapOf("eventId" to eventId, "userId" to userId, "enabled" to enabled)).await()
    }

    suspend fun eventsForUser(userId: String): List<SnapEvent> {
        val refs = db.collection("users").document(userId).collection("eventRefs").get().await()
        return refs.documents.mapNotNull { ref ->
            val eventId = ref.getString("eventId") ?: ref.id
            runCatching { fetchEvent(eventId) }.getOrNull()
        }.sortedByDescending { it.startsAt }
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
        return decodeEvent(eventMap["id"] as? String ?: error("Missing event id"), eventMap)
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
        startsAt = Instant.ofEpochMilli(data.long("startsAtMillis")),
        endsAt = Instant.ofEpochMilli(data.long("endsAtMillis")),
        photoWindowVersion = data.intOrNull("photoWindowVersion"),
        photoWindowTimeZoneId = data.stringOrNull("photoWindowTimeZoneId"),
        photoWindowStartDayNumber = data.intOrNull("photoWindowStartDayNumber"),
        photoWindowEndDayNumber = data.intOrNull("photoWindowEndDayNumber"),
        status = enumValueOrDefault(data.stringOrNull("status"), EventStatus.active),
        createdAt = Instant.ofEpochMilli(data.long("createdAtMillis")),
        updatedAt = Instant.ofEpochMilli(data.longOrNull("updatedAtMillis") ?: data.long("createdAtMillis"))
    )

    private fun decodeMember(data: Map<*, *>): EventMember = EventMember(
        userId = data.string("userId"),
        membershipId = data.stringOrNull("membershipId"),
        displayName = data.stringOrNull("displayName"),
        role = enumValueOrDefault(data.stringOrNull("role"), EventMember.Role.participant),
        joinedAt = Instant.ofEpochMilli(data.long("joinedAtMillis")),
        sharingEnabled = data["sharingEnabled"] as? Boolean ?: true,
        lastSyncAt = data.longOrNull("lastSyncAtMillis")?.let(Instant::ofEpochMilli),
        faceTemplateVersion = data.intOrNull("faceTemplateVersion") ?: 0
    )

    private fun Map<*, *>.string(key: String) = stringOrNull(key) ?: error("Missing $key")
    private fun Map<*, *>.stringOrNull(key: String) = this[key] as? String
    private fun Map<*, *>.long(key: String) = longOrNull(key) ?: error("Missing $key")
    private fun Map<*, *>.longOrNull(key: String) = (this[key] as? Number)?.toLong()
    private fun Map<*, *>.intOrNull(key: String) = (this[key] as? Number)?.toInt()
    private inline fun <reified T: Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
