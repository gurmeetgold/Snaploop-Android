package com.snaploop.app.ui

import com.snaploop.app.core.DeepLinkParser

/** Pure manual Join Event entry semantics mirrored from pinned iOS EnterCodeView. */
internal object JoinEventEntryParityPolicy {
    const val INVALID_MESSAGE = "That QR code, Event code, or invite link isn't valid."

    fun canContinue(value: String): Boolean = value.trim().isNotEmpty()

    fun validationError(value: String): String? =
        if (DeepLinkParser.parseManual(value.trim()) != null) null else INVALID_MESSAGE
}
