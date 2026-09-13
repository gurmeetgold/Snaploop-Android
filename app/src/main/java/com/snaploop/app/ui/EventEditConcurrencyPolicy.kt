package com.snaploop.app.ui

import com.snaploop.app.model.SnapEvent

/** Prevents a stale Edit Event form from overwriting a newer user-editable Event revision. */
internal object EventEditConcurrencyPolicy {
    fun isFresh(opened: SnapEvent, latest: SnapEvent): Boolean =
        opened.id == latest.id &&
            opened.name == latest.name &&
            opened.category == latest.category &&
            opened.coverImagePath == latest.coverImagePath &&
            opened.locationName == latest.locationName &&
            opened.startsAt == latest.startsAt &&
            opened.endsAt == latest.endsAt &&
            opened.photoWindowVersion == latest.photoWindowVersion &&
            opened.photoWindowTimeZoneId == latest.photoWindowTimeZoneId &&
            opened.photoWindowStartDayNumber == latest.photoWindowStartDayNumber &&
            opened.photoWindowEndDayNumber == latest.photoWindowEndDayNumber &&
            opened.status == latest.status

    const val STALE_MESSAGE =
        "This Event changed while you were editing it. Close Edit Event and reopen it to load the latest version before saving."
}
