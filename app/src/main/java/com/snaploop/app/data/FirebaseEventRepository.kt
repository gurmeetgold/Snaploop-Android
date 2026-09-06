package com.snaploop.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FirebaseErrorMapper
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.core.awaitResult
import com.snaploop.app.domain.Event
import com.snaploop.app.domain.EventCategory
import com.snaploop.app.domain.EventMember
import com.snaploop.app.domain.EventParticipant
import com.snaploop.app.domain.EventStatus
import com.snaploop.app.domain.FaceTemplatePose
import com.snaploop.app.domain.FaceTemplateRecord
import java.time.Instant
import java.time.ZoneId

/** Android port of the production iOS FirebaseEventRepository contract. Trusted writes use callables. */
class FirebaseEventRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val callable: FirebaseCallableClient = FirebaseCallableClient(),
) {
    suspend fun createEvent(event: Event) {
        val payload = linkedMapOf<String, Any?>(
            "id" to event.id,
            "joinCode" to event.joinCode,
            "inviteToken" to event.inviteToken,
            "creatorUserId" to event.creatorUserId,
            "name" to event.name,
            "category" to event.category.wireValue,
            "status" to event.status.wireValue,
            "createdAtMillis" to event.createdAtMillis,
            "updatedAtMillis" to event.updatedAtMillis,
            "coverImagePath" to event.coverImagePath,
            "locationName" to event.locationName,
        )
        payload.putAll(datePayload(event))
        callable.call("createEvent", payload)
    }

    suspend fun fetchEvent(id: String): Event = try {
        val snapshot = db.collection("events").document(id).get().awaitResult()
        if (!snapshot.exists()) throw SnapLoopException.EventNotFound()
        decodeEvent(snapshot)
    } catch (e: SnapLoopException) { throw e }
      catch (t: Throwable) { throw FirebaseErrorMapper.firestore(t) }

    suspend fun resolveJoinCode(joinCode: String): Event = decodeCallableEvent(callable.call("resolveInvite", mapOf("joinCode" to joinCode)))
    suspend fun resolveInviteToken(inviteToken: String): Event = decodeCallableEvent(callable.call("resolveInvite", mapOf("inviteToken" to inviteToken)))

    suspend fun updateEventDetails(eventId: String, name: String, category: EventCategory, coverImagePath: String?, locationName: String?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw SnapLoopException.InvalidData("Event name cannot be empty")
        callable.call("updateEventManaged", mapOf(
            "eventId" to eventId, "name" to trimmed, "category" to category.wireValue,
            "coverImagePath" to coverImagePath, "locationName" to locationName,
        ))
    }

    suspend fun updateEventDates(event: Event) {
        val payload = linkedMapOf<String, Any?>("eventId" to event.id)
        payload.putAll(datePayload(event))
        callable.call("updateEventManaged", payload)
    }

    suspend fun setStatus(eventId: String, status: EventStatus) {
        callable.call("setEventStatus", mapOf("eventId" to eventId, "status" to status.wireValue))
    }

    suspend fun join(eventId: String) { callable.call("joinEvent", mapOf("eventId" to eventId)) }
    suspend fun leave(eventId: String, userId: String) { callable.call("leaveEvent", mapOf("eventId" to eventId, "userId" to userId)) }
    suspend fun setSharing(eventId: String, userId: String, enabled: Boolean) {
        callable.call("setSharing", mapOf("eventId" to eventId, "userId" to userId, "enabled" to enabled))
    }

    suspend fun members(eventId: String): List<EventMember> {
        val wrapper = callable.call("listEventMembers", mapOf("eventId" to eventId)).asStringMap()
        val rows = wrapper["members"] as? List<*> ?: throw SnapLoopException.InvalidData("listEventMembers returned malformed data")
        return rows.map { decodeMember(it.asStringMap()) }.sortedBy { it.joinedAtMillis }
    }

    suspend fun participants(eventId: String): List<EventParticipant> = try {
        db.collection("events").document(eventId).collection("participants").get().awaitResult().documents
            .map { decodeParticipant(it) }.sortedBy { it.joinedAtMillis }
    } catch (t: Throwable) { throw FirebaseErrorMapper.firestore(t) }

    suspend fun eventsForUser(userId: String): List<Event> = try {
        val refs = db.collection("users").document(userId).collection("eventRefs").get().awaitResult().documents
        buildList {
            for (doc in refs) {
                val eventId = doc.getString("eventId") ?: doc.id
                try { add(fetchEvent(eventId)) }
                catch (_: SnapLoopException.EventNotFound) { }
                catch (_: SnapLoopException.PermissionDenied) { }
            }
        }.sortedByDescending { it.startsAtMillis }
    } catch (e: SnapLoopException) { throw e }
      catch (t: Throwable) { throw FirebaseErrorMapper.firestore(t) }

    private fun decodeEvent(snapshot: DocumentSnapshot): Event {
        val data = snapshot.data ?: throw SnapLoopException.InvalidData("events/${snapshot.id} has no data")
        return decodeEventMap(snapshot.id, data, timestamps = true)
    }

    private fun decodeCallableEvent(raw: Any?): Event {
        val wrapper = raw.asStringMap()
        val event = wrapper["event"].asStringMap()
        val id = event.string("id")
        return decodeEventMap(id, event, timestamps = false)
    }

    private fun decodeEventMap(id: String, data: Map<String, Any?>, timestamps: Boolean): Event {
        fun date(field: String, millisField: String): Long = if (timestamps) data.timestampMillis(field) else data.long(millisField)
        return Event(
            id = id,
            joinCode = data.string("joinCode"),
            inviteToken = data.string("inviteToken"),
            creatorUserId = data.string("creatorUserId"),
            name = data.string("name"),
            category = EventCategory.fromWire(data.string("category")) ?: throw SnapLoopException.InvalidData("Unknown Event category"),
            coverImagePath = data.optionalString("coverImagePath"),
            locationName = data.optionalString("locationName"),
            startsAtMillis = date("startsAt", "startsAtMillis"),
            endsAtMillis = date("endsAt", "endsAtMillis"),
            photoWindowVersion = data.optionalInt("photoWindowVersion"),
            photoWindowTimeZoneId = data.optionalString("photoWindowTimeZoneId"),
            photoWindowStartDayNumber = data.optionalInt("photoWindowStartDayNumber"),
            photoWindowEndDayNumber = data.optionalInt("photoWindowEndDayNumber"),
            status = EventStatus.fromWire(data.string("status")) ?: throw SnapLoopException.InvalidData("Unknown Event status"),
            createdAtMillis = date("createdAt", "createdAtMillis"),
            updatedAtMillis = if (timestamps) data.optionalTimestampMillis("updatedAt") ?: date("createdAt", "createdAtMillis") else data.optionalLong("updatedAtMillis") ?: date("createdAt", "createdAtMillis"),
        )
    }

    private fun decodeMember(data: Map<String, Any?>): EventMember = EventMember(
        userId = data.string("userId"),
        membershipId = data.optionalString("membershipId"),
        displayName = data.optionalString("displayName"),
        role = EventMember.Role.fromWire(data.string("role")) ?: throw SnapLoopException.InvalidData("Unknown member role"),
        joinedAtMillis = data.long("joinedAtMillis"),
        sharingEnabled = data["sharingEnabled"] as? Boolean ?: true,
        lastSyncAtMillis = data.optionalLong("lastSyncAtMillis"),
        faceTemplateVersion = data.optionalInt("faceTemplateVersion") ?: 1,
    )

    private fun decodeParticipant(snapshot: DocumentSnapshot): EventParticipant {
        val data = snapshot.data ?: throw SnapLoopException.InvalidData("participant ${snapshot.id} has no data")
        val vector = data.floatVector("faceEmbedding")
        val normalized = Embeddings.normalize(vector) ?: throw SnapLoopException.InvalidData("participant ${snapshot.id} has invalid faceEmbedding")
        val joined = data.timestampMillis("joinedAt")
        val templates = (data["faceTemplates"] as? List<*>).orEmpty().mapNotNull { raw ->
            runCatching {
                val item = raw.asStringMap()
                val embedding = Embeddings.normalize(item.floatVector("embedding")) ?: return@runCatching null
                val pose = FaceTemplatePose.fromWire(item.string("pose")) ?: return@runCatching null
                FaceTemplateRecord(
                    id = item.optionalString("id") ?: "legacy-${pose.wireValue}",
                    embedding = embedding,
                    pose = pose,
                    quality = item.optionalDouble("quality") ?: 1.0,
                    createdAtMillis = item.optionalTimestampMillis("createdAt") ?: joined,
                )
            }.getOrNull()
        }
        return EventParticipant(
            userId = data.string("userId"),
            membershipId = data.optionalString("membershipId"),
            displayName = data.optionalString("displayName"),
            faceIdentityId = data.optionalString("faceIdentityId"),
            faceEmbedding = normalized,
            faceTemplates = templates,
            faceProfileVersion = data.optionalInt("faceProfileVersion") ?: 1,
            joinedAtMillis = joined,
        )
    }

    private fun datePayload(event: Event): Map<String, Any?> {
        val zone = runCatching { ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault())
        fun offsetMinutes(ms: Long) = zone.rules.getOffset(Instant.ofEpochMilli(ms)).totalSeconds / 60
        return linkedMapOf<String, Any?>(
            "startsAtMillis" to event.startsAtMillis,
            "endsAtMillis" to event.endsAtMillis,
            "startsAtOffsetMinutes" to offsetMinutes(event.startsAtMillis),
            "endsAtOffsetMinutes" to offsetMinutes(event.endsAtMillis),
            "nowOffsetMinutes" to offsetMinutes(System.currentTimeMillis()),
        ).apply {
            if (event.photoWindowVersion == Event.CANONICAL_PHOTO_WINDOW_VERSION && !event.photoWindowTimeZoneId.isNullOrBlank()) {
                put("photoWindowVersion", Event.CANONICAL_PHOTO_WINDOW_VERSION)
                put("photoWindowTimeZoneId", event.photoWindowTimeZoneId)
            }
        }
    }
}

private fun Any?.asStringMap(): Map<String, Any?> {
    val raw = this as? Map<*, *> ?: throw SnapLoopException.InvalidData("Expected object from backend")
    return raw.entries.associate { (k, v) -> (k as? String ?: throw SnapLoopException.InvalidData("Backend object had a non-string key")) to v }
}
private fun Map<String, Any?>.string(key: String): String = optionalString(key) ?: throw SnapLoopException.InvalidData("Missing $key")
private fun Map<String, Any?>.optionalString(key: String): String? = (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
private fun Map<String, Any?>.long(key: String): Long = optionalLong(key) ?: throw SnapLoopException.InvalidData("Missing $key")
private fun Map<String, Any?>.optionalLong(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<String, Any?>.optionalInt(key: String): Int? = (this[key] as? Number)?.toInt()
private fun Map<String, Any?>.optionalDouble(key: String): Double? = (this[key] as? Number)?.toDouble()
private fun Map<String, Any?>.timestampMillis(key: String): Long = optionalTimestampMillis(key) ?: throw SnapLoopException.InvalidData("Missing $key")
private fun Map<String, Any?>.optionalTimestampMillis(key: String): Long? = (this[key] as? Timestamp)?.toDate()?.time
private fun Map<String, Any?>.floatVector(key: String): FloatArray {
    val values = this[key] as? List<*> ?: throw SnapLoopException.InvalidData("Missing $key")
    return FloatArray(values.size) { index -> (values[index] as? Number)?.toFloat() ?: Float.NaN }
}
