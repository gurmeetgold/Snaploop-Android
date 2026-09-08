package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.SnapEvent
import java.time.LocalDate
import java.time.ZoneId

/** Pure edit semantics mirrored from pinned iOS EventFactory.applyEdit/EditEventView. */
internal object EventEditParityPolicy {
    data class Plan(
        val event: SnapEvent,
        val datesChanged: Boolean,
        val hasChanges: Boolean,
    )

    fun plan(
        current: SnapEvent,
        name: String,
        category: EventCategory,
        locationName: String?,
        startsOn: LocalDate,
        endsOn: LocalDate,
    ): Plan {
        val cleanName = name.trim()
        val cleanLocation = locationName?.trim()?.takeIf { it.isNotEmpty() }
        val zone = eventZone(current)
        val currentStart = current.startsAt.atZone(zone).toLocalDate()
        val currentEnd = current.endsAt.atZone(zone).toLocalDate()
        val datesChanged = startsOn != currentStart || endsOn != currentEnd
        val detailsChanged = cleanName != current.name ||
            category != current.category ||
            cleanLocation != current.locationName

        val updated = if (datesChanged) {
            current.copy(
                name = cleanName,
                category = category,
                locationName = cleanLocation,
                startsAt = startsOn.atStartOfDay(zone).toInstant(),
                endsAt = endsOn.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1),
                photoWindowVersion = SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION,
                photoWindowTimeZoneId = zone.id,
                photoWindowStartDayNumber = startsOn.toEpochDay().toInt(),
                photoWindowEndDayNumber = endsOn.toEpochDay().toInt(),
            )
        } else {
            // Details-only edits deliberately preserve every date/photo-window field byte-for-byte.
            current.copy(
                name = cleanName,
                category = category,
                locationName = cleanLocation,
            )
        }

        return Plan(
            event = updated,
            datesChanged = datesChanged,
            hasChanges = detailsChanged || datesChanged,
        )
    }

    fun eventZone(event: SnapEvent): ZoneId = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
}
