package com.snaploop.app.core

import android.net.Uri

data class JoinIntent(
    val code: String? = null,
    val token: String? = null,
    val action: InviteAction = InviteAction.REVIEW,
)

enum class InviteAction { REVIEW, ACCEPT, DECLINE }

/**
 * Android mirror of the iOS DeepLinkRouter / InviteLink contract.
 *
 * SnapLoop has used two official Hosting domains during development. Shared invites are a product
 * contract, not a build-variant contract, so every installed Android build accepts both official
 * hosts while all newly-generated links use the production/canonical host. This is important for
 * iPhone <-> Android testing and for links that survive an app upgrade from a debug/beta build.
 *
 * Supported forms:
 * - https://getsnaploop.web.app/e/<22-char-token>
 * - https://snaploop-dev.web.app/e/<22-char-token> (legacy/dev compatibility)
 * - https://<official-host>/c/<6-char-code>
 * - snaploop://e/<token>
 * - snaploop://c/<code>
 * - snaploop://join?token=<token>
 * - pasted share copy containing one of the URLs above
 * - bare six-character Event code
 */
object DeepLinkParser {
    private const val JOIN_CODE_LENGTH = 6
    private const val TOKEN_LENGTH = 22
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    const val CANONICAL_HTTPS_HOST = "getsnaploop.web.app"
    const val LEGACY_DEV_HTTPS_HOST = "snaploop-dev.web.app"

    private val urlInText = Regex("(?i)(?:https|snaploop)://[^\\s]+")

    /** Canonical host used for every newly shared QR code or web invite link. */
    fun allowedHttpsHost(): String = CANONICAL_HTTPS_HOST

    fun parse(uri: Uri, allowedHttpsHost: String = CANONICAL_HTTPS_HOST): JoinIntent? {
        val scheme = uri.scheme?.lowercase()
        val trusted = when (scheme) {
            "https" -> {
                val host = uri.host?.lowercase()
                host != null && host in trustedHttpsHosts(allowedHttpsHost)
            }
            "snaploop" -> true
            else -> false
        }
        if (!trusted) return null

        val action = when (uri.getQueryParameter("action")?.lowercase()) {
            "accept" -> InviteAction.ACCEPT
            "decline" -> InviteAction.DECLINE
            else -> InviteAction.REVIEW
        }

        val queryToken = normalizeToken(uri.getQueryParameter("token"))
        if (queryToken != null) return JoinIntent(token = queryToken, action = action)

        val pathSegments = uri.pathSegments.filter { it.isNotBlank() }.toMutableList()
        if (scheme == "snaploop") {
            val host = uri.host
            if (!host.isNullOrBlank() && host != "join") pathSegments.add(0, host)
        }

        if (pathSegments.size >= 2) {
            return when (pathSegments[0].lowercase()) {
                "e" -> normalizeToken(pathSegments[1])?.let { JoinIntent(token = it, action = action) }
                "c" -> normalizeCode(pathSegments[1])?.let {
                    JoinIntent(
                        code = it,
                        action = if (action == InviteAction.DECLINE) InviteAction.REVIEW else action,
                    )
                }
                else -> null
            }
        }

        // Legacy/query compatibility without trusting arbitrary last path segments.
        normalizeCode(uri.getQueryParameter("code"))?.let {
            return JoinIntent(
                code = it,
                action = if (action == InviteAction.DECLINE) InviteAction.REVIEW else action,
            )
        }
        return null
    }

    fun parseManual(raw: String, allowedHttpsHost: String = CANONICAL_HTTPS_HOST): JoinIntent? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Messaging apps often paste the complete share sentence instead of only the URL. Extract
        // the canonical link rather than handing the whole sentence to Uri.parse(). Trim common
        // sentence/Markdown punctuation that can be attached to a copied URL.
        val extractedUrl = urlInText.find(trimmed)?.value?.trimEnd { it in ".,;:!?)]}>\"'" }
        if (extractedUrl != null) {
            return runCatching { parse(Uri.parse(extractedUrl), allowedHttpsHost) }.getOrNull()
        }

        return normalizeCode(trimmed)?.let { JoinIntent(code = it) }
    }

    fun normalizeCode(raw: String?): String? {
        val value = raw.orEmpty().uppercase().filterNot { it == ' ' || it == '-' }
        if (value.length != JOIN_CODE_LENGTH || value.any { it !in CODE_ALPHABET }) return null
        return value
    }

    fun formatCode(raw: String): String {
        val code = normalizeCode(raw) ?: return raw
        return code.substring(0, 3) + "-" + code.substring(3)
    }

    fun normalizeToken(raw: String?): String? {
        val value = raw.orEmpty()
        if (value.length != TOKEN_LENGTH || value.any { it !in TOKEN_ALPHABET }) return null
        return value
    }

    fun inviteUrl(token: String, allowedHttpsHost: String = CANONICAL_HTTPS_HOST): String {
        val canonical = normalizeToken(token) ?: token
        return "https://$allowedHttpsHost/e/$canonical"
    }

    fun shareText(
        eventName: String,
        inviterName: String?,
        token: String,
        allowedHttpsHost: String = CANONICAL_HTTPS_HOST,
    ): String {
        val cleaned = inviterName?.trim().orEmpty()
        val intro = if (cleaned.isEmpty()) {
            "You're invited to join \"$eventName\" on SnapLoop:"
        } else {
            "$cleaned invited you to join \"$eventName\" on SnapLoop:"
        }
        return "$intro\n${inviteUrl(token, allowedHttpsHost)}"
    }

    private fun trustedHttpsHosts(extraHost: String): Set<String> = setOf(
        CANONICAL_HTTPS_HOST,
        LEGACY_DEV_HTTPS_HOST,
        extraHost.trim().lowercase(),
    ).filterTo(linkedSetOf()) { it.isNotEmpty() }
}
