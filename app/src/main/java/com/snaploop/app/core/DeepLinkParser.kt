package com.snaploop.app.core

import android.net.Uri
import com.snaploop.app.BuildConfig

data class JoinIntent(
    val code: String? = null,
    val token: String? = null,
    val action: InviteAction = InviteAction.REVIEW,
)

enum class InviteAction { REVIEW, ACCEPT, DECLINE }

/**
 * Android mirror of the iOS DeepLinkRouter / InviteLink contract.
 *
 * Supported forms:
 * - https://<trusted-host>/e/<22-char-token>
 * - https://<trusted-host>/c/<6-char-code>
 * - snaploop://e/<token>
 * - snaploop://c/<code>
 * - snaploop://join?token=<token>
 * - pasted full URL or bare six-character code
 */
object DeepLinkParser {
    private const val JOIN_CODE_LENGTH = 6
    private const val TOKEN_LENGTH = 22
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun allowedHttpsHost(): String = if (BuildConfig.DEBUG) "snaploop-dev.web.app" else "getsnaploop.web.app"

    fun parse(uri: Uri, allowedHttpsHost: String = allowedHttpsHost()): JoinIntent? {
        val scheme = uri.scheme?.lowercase()
        val trusted = when (scheme) {
            "https" -> uri.host.equals(allowedHttpsHost, ignoreCase = true)
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
                "c" -> normalizeCode(pathSegments[1])?.let { JoinIntent(code = it, action = if (action == InviteAction.DECLINE) InviteAction.REVIEW else action) }
                else -> null
            }
        }

        // Legacy/query compatibility without trusting arbitrary last path segments.
        normalizeCode(uri.getQueryParameter("code"))?.let {
            return JoinIntent(code = it, action = if (action == InviteAction.DECLINE) InviteAction.REVIEW else action)
        }
        return null
    }

    fun parseManual(raw: String, allowedHttpsHost: String = allowedHttpsHost()): JoinIntent? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("://")) {
            return runCatching { parse(Uri.parse(trimmed), allowedHttpsHost) }.getOrNull()
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

    fun inviteUrl(token: String, allowedHttpsHost: String = allowedHttpsHost()): String {
        val canonical = normalizeToken(token) ?: token
        return "https://$allowedHttpsHost/e/$canonical"
    }

    fun shareText(eventName: String, inviterName: String?, token: String): String {
        val cleaned = inviterName?.trim().orEmpty()
        val intro = if (cleaned.isEmpty()) {
            "You're invited to join \"$eventName\" on SnapLoop:"
        } else {
            "$cleaned invited you to join \"$eventName\" on SnapLoop:"
        }
        return "$intro\n${inviteUrl(token)}"
    }
}
