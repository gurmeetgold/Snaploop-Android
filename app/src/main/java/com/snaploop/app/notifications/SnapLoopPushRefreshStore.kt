package com.snaploop.app.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Durable wake-up state for product pushes.
 *
 * Push payloads are hints, never navigation authority. Persist only the fact that
 * authenticated state must be refreshed. Invite tokens, Event IDs, notification
 * copy, photo identifiers and biometric data are deliberately not written here.
 */
object SnapLoopPushRefreshStore {
    private const val PREFS = "snaploop.push.refresh"
    private const val KEY_PENDING = "pending"

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    @Volatile
    private var preferences: SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _pending.value = preferences?.getBoolean(KEY_PENDING, false) == true
    }

    @Synchronized
    fun markPending(context: Context) {
        initialize(context)
        _pending.value = true
        preferences?.edit()?.putBoolean(KEY_PENDING, true)?.apply()
    }

    @Synchronized
    fun clear() {
        _pending.value = false
        preferences?.edit()?.remove(KEY_PENDING)?.apply()
    }
}
