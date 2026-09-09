package com.snaploop.app.notifications

import com.snaploop.app.core.DeepLinkParser

enum class SnapLoopNotificationKind { INVITE, EVENT_PHOTOS, GENERIC }

data class SnapLoopNotificationPayload(
    val kind: SnapLoopNotificationKind,
    val inviteToken: String? = null,
    val eventId: String? = null,
    val matchCount: Int? = null,
    val title: String = DEFAULT_TITLE,
    val body: String,
) {
    companion object {
        const val DEFAULT_TITLE = "SnapLoop"
    }
}

/**
 * Cross-platform notification data contract mirrored from pinned iOS NotificationDelegate and
 * EventNotificationClient. Only invitation tokens and Event IDs are navigation-bearing values.
 * Copy is bounded before it reaches Android notifications and no photo/face identifiers are parsed.
 */
object SnapLoopNotificationContract {
    const val KEY_INVITE_TOKEN = "inviteToken"
    const val KEY_EVENT_ID = "eventId"
    const val KEY_MATCH_COUNT = "matchCount"
    const val KEY_COUNT = "count"

    private const val MAX_EVENT_ID_LENGTH = 128
    private const val MAX_TITLE_LENGTH = 80
    private const val MAX_BODY_LENGTH = 240
    private val eventIdPattern = Regex("^[A-Za-z0-9_-]{1,$MAX_EVENT_ID_LENGTH}$")
    private val whitespace = Regex("\\s+")

    fun parse(
        data: Map<String, String>,
        notificationTitle: String? = null,
        notificationBody: String? = null,
    ): SnapLoopNotificationPayload {
        val inviteToken = DeepLinkParser.normalizeToken(data[KEY_INVITE_TOKEN])
        val eventId = normalizeEventId(data[KEY_EVENT_ID])
        val matchCount = (data[KEY_MATCH_COUNT] ?: data[KEY_COUNT])
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in 1..999 }

        val kind = when {
            inviteToken != null -> SnapLoopNotificationKind.INVITE
            eventId != null -> SnapLoopNotificationKind.EVENT_PHOTOS
            else -> SnapLoopNotificationKind.GENERIC
        }
        val title = sanitizeCopy(notificationTitle, MAX_TITLE_LENGTH)
            ?: SnapLoopNotificationPayload.DEFAULT_TITLE
        val body = sanitizeCopy(notificationBody, MAX_BODY_LENGTH)
            ?: fallbackBody(kind, matchCount)

        return SnapLoopNotificationPayload(
            kind = kind,
            inviteToken = inviteToken,
            eventId = eventId,
            matchCount = matchCount,
            title = title,
            body = body,
        )
    }

    fun normalizeEventId(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        return value.takeIf { eventIdPattern.matches(it) }
    }

    /** Stable across process restarts so invite decline and replacement Event alerts can cancel. */
    fun notificationId(payload: SnapLoopNotificationPayload): Int = when (payload.kind) {
        SnapLoopNotificationKind.INVITE -> stableId("invite:${payload.inviteToken}")
        SnapLoopNotificationKind.EVENT_PHOTOS -> stableId("event:${payload.eventId}")
        SnapLoopNotificationKind.GENERIC -> stableId("generic:${payload.title}:${payload.body}")
    }

    fun inviteNotificationId(token: String): Int? =
        DeepLinkParser.normalizeToken(token)?.let { stableId("invite:$it") }

    private fun fallbackBody(kind: SnapLoopNotificationKind, matchCount: Int?): String = when (kind) {
        SnapLoopNotificationKind.INVITE -> "You have a new SnapLoop Event invitation."
        SnapLoopNotificationKind.EVENT_PHOTOS -> when (matchCount) {
            1 -> "We found a photo of you in an Event."
            null -> "New photos of you were found in an Event."
            else -> "We found $matchCount photos of you in an Event."
        }
        SnapLoopNotificationKind.GENERIC -> "Open SnapLoop to see this update."
    }

    private fun sanitizeCopy(raw: String?, maxLength: Int): String? {
        val normalized = raw
            ?.replace(whitespace, " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        return normalized.take(maxLength)
    }

    private fun stableId(value: String): Int {
        val positive = value.hashCode() and Int.MAX_VALUE
        return if (positive == 0) 1 else positive
    }
}
