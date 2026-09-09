package com.snaploop.app.ui

import com.snaploop.app.model.SnapEvent

/** Prevents a stale Edit Event form from overwriting a newer server revision. */
internal object EventEditConcurrencyPolicy {
    fun isFresh(opened: SnapEvent, latest: SnapEvent): Boolean =
        opened.id == latest.id && opened.updatedAt == latest.updatedAt

    const val STALE_MESSAGE =
        "This Event changed while you were editing it. The latest version has been loaded; review the changes and try again."
}
