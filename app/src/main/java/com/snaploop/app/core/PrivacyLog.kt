package com.snaploop.app.core

import android.util.Log

object PrivacyLog {
    private val forbiddenKeys = setOf("embedding","selfie","phone","otp","token","signedurl","asseturi","similarity","fullname","invitetoken")
    fun event(tag: String, message: String, fields: Map<String, Any?> = emptyMap()) {
        val safe = fields.filterKeys { key -> forbiddenKeys.none { key.lowercase().contains(it) } }
        Log.i(tag, if (safe.isEmpty()) message else "$message ${safe.keys.sorted().joinToString(prefix="fields=[", postfix="]")}")
    }
}
