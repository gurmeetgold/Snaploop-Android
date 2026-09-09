package com.snaploop.app.ui

import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.SnapEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure edit semantics mirrored from pinned iOS EventFactory.applyEdit/EditEventView. */
internal object EventEditParityPolicy {
    const val MVP_MAXIMUM_DURATION_DAYS = 15
    const val SUGGESTED_END_OFFSET_DAYS = 3

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

    /**
     * Mirrors pinned EditEventView: opening Edit never clamps historical dates. Only when the
     * organizer deliberately changes Starts do we repair an end date that is now before Starts or
     * beyond the maximum duration, preferring Start + 3 days and capping it to the configured max.
     */
    fun repairEndAfterStartChange(
        newStart: LocalDate,
        currentEnd: LocalDate,
        configuredMaximumDurationDays: Int = MVP_MAXIMUM_DURATION_DAYS,
    ): LocalDate {
        val maxDays = minOf(MVP_MAXIMUM_DURATION_DAYS, configuredMaximumDurationDays.coerceAtLeast(1))
        val dayDistance = ChronoUnit.DAYS.between(newStart, currentEnd)
        if (dayDistance in 0..maxDays.toLong()) return currentEnd
        val maxEnd = newStart.plusDays(maxDays.toLong())
        return minOf(maxEnd, newStart.plusDays(SUGGESTED_END_OFFSET_DAYS.toLong()))
    }

    fun eventZone(event: SnapEvent): ZoneId = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
}
