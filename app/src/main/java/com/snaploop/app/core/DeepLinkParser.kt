package com.snaploop.app.core

import android.net.Uri

data class JoinIntent(val code: String?, val token: String?)

object DeepLinkParser {
    private val codeRegex = Regex("^[A-Z0-9]{4,12}$")
    fun parse(uri: Uri, allowedHttpsHost: String): JoinIntent? {
        val trusted = when (uri.scheme?.lowercase()) {
            "https" -> uri.host.equals(allowedHttpsHost, ignoreCase = true)
            "snaploop" -> true
            else -> false
        }
        if (!trusted) return null
        val code = (uri.getQueryParameter("code") ?: uri.lastPathSegment)?.uppercase()?.takeIf { codeRegex.matches(it) }
        val token = uri.getQueryParameter("token")?.takeIf { it.length in 16..512 && it.all { ch -> ch.isLetterOrDigit() || ch in "-_" } }
        if (code == null && token == null) return null
        return JoinIntent(code, token)
    }
}
