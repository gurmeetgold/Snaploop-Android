package com.snaploop.app.data

import com.snaploop.app.model.SnapEvent
import java.time.Instant
import java.time.ZoneId

/** Callable payload mirrored from pinned iOS EventManagementClient.update. */
internal fun managedEventUpdatePayload(
    event: SnapEvent,
    includeDates: Boolean,
    expectedUpdatedAt: Instant,
    now: Instant = Instant.now(),
): Map<String, Any?> {
    val payload = mutableMapOf<String, Any?>(
        "eventId" to event.id,
        "name" to event.name,
        "category" to event.category.name,
        "coverImagePath" to event.coverImagePath,
        "locationName" to event.locationName,
        "expectedUpdatedAtMillis" to expectedUpdatedAt.toEpochMilli(),
    )
    if (includeDates) {
        payload["startsAtMillis"] = event.startsAt.toEpochMilli()
        payload["endsAtMillis"] = event.endsAt.toEpochMilli()
        payload["startsAtOffsetMinutes"] = eventOffsetMinutes(event.startsAt, event.photoWindowTimeZoneId)
        payload["endsAtOffsetMinutes"] = eventOffsetMinutes(event.endsAt, event.photoWindowTimeZoneId)
        payload["nowOffsetMinutes"] = eventOffsetMinutes(now, event.photoWindowTimeZoneId)
        event.photoWindowVersion?.let { payload["photoWindowVersion"] = it }
        event.photoWindowTimeZoneId?.takeIf { it.isNotBlank() }?.let { payload["photoWindowTimeZoneId"] = it }
    }
    return payload
}

private fun eventOffsetMinutes(instant: Instant, timeZoneId: String?): Int {
    val zone = runCatching { ZoneId.of(timeZoneId ?: ZoneId.systemDefault().id) }
        .getOrDefault(ZoneId.systemDefault())
    return zone.rules.getOffset(instant).totalSeconds / 60
}
