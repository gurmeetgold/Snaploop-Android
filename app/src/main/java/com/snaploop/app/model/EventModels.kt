package com.snaploop.app.model

import java.time.Instant

enum class EventCategory { trip, wedding, party, birthday, conference, family, sports, other }
enum class EventStatus { active, endedByOrganizer, deletedByOrganizer, expired }

data class SnapEvent(
    val id: String,
    val joinCode: String,
    val inviteToken: String,
    val creatorUserId: String,
    val name: String,
    val category: EventCategory = EventCategory.other,
    val coverImagePath: String? = null,
    val locationName: String? = null,
    val startsAt: Instant,
    val endsAt: Instant,
    val photoWindowVersion: Int? = null,
    val photoWindowTimeZoneId: String? = null,
    val photoWindowStartDayNumber: Int? = null,
    val photoWindowEndDayNumber: Int? = null,
    val status: EventStatus = EventStatus.active,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init { require(endsAt >= startsAt) }
    val usesCanonicalPhotoWindow: Boolean
        get() = photoWindowVersion == CANONICAL_PHOTO_WINDOW_VERSION && !photoWindowTimeZoneId.isNullOrBlank()

    companion object { const val CANONICAL_PHOTO_WINDOW_VERSION = 1 }
}

data class EventMember(
    val userId: String,
    val membershipId: String? = null,
    val displayName: String? = null,
    val role: Role,
    val joinedAt: Instant,
    val sharingEnabled: Boolean = true,
    val lastSyncAt: Instant? = null,
    val faceTemplateVersion: Int
) {
    enum class Role { organizer, admin, participant }
}
