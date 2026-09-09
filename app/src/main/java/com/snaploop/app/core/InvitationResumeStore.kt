package com.snaploop.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Durable invitation navigation context used only to resume an invitation after
 * authentication / Face Setup rebuilding AppUiState.
 *
 * Only the invitation token/code and navigation action are persisted. No face
 * embeddings, captures, photo identifiers or other biometric/photo data are
 * written here. The values are validated again through the normal invite
 * resolver before any membership action is performed.
 */
data class InvitationResumeContext(
    val token: String? = null,
    val code: String? = null,
    val action: InviteAction = InviteAction.REVIEW,
    val canDecline: Boolean = false,
    val awaitingFaceSetup: Boolean = false,
)

object InvitationResumeStore {
    private const val PREFS = "snaploop.invitation.resume"
    private const val KEY_TOKEN = "token"
    private const val KEY_CODE = "code"
    private const val KEY_ACTION = "action"
    private const val KEY_CAN_DECLINE = "can_decline"
    private const val KEY_AWAITING_FACE_SETUP = "awaiting_face_setup"

    private val _state = MutableStateFlow<InvitationResumeContext?>(null)
    val state: StateFlow<InvitationResumeContext?> = _state.asStateFlow()

    @Volatile
    private var preferences: SharedPreferences? = null

    /** Call once from the application host before invitation routing begins. */
    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = readPersisted(preferences!!)
    }

    fun capture(intent: JoinIntent) {
        set(
            InvitationResumeContext(
                token = intent.token,
                code = intent.code,
                action = intent.action,
                canDecline = intent.action == InviteAction.REVIEW && !intent.token.isNullOrBlank(),
                awaitingFaceSetup = false,
            ),
        )
    }

    fun beginFaceSetup(
        fallbackToken: String,
        action: InviteAction = _state.value?.action ?: InviteAction.REVIEW,
    ) {
        val current = _state.value
        set(
            InvitationResumeContext(
                token = current?.token ?: fallbackToken,
                code = current?.code,
                action = action,
                canDecline = current?.canDecline == true,
                awaitingFaceSetup = true,
            ),
        )
    }

    fun markFaceSetupResumed() {
        _state.value?.copy(awaitingFaceSetup = false)?.let(::set)
    }

    fun clear() {
        _state.value = null
        preferences?.edit()?.clear()?.apply()
    }

    private fun set(value: InvitationResumeContext) {
        _state.value = value
        preferences?.edit()
            ?.putString(KEY_TOKEN, value.token)
            ?.putString(KEY_CODE, value.code)
            ?.putString(KEY_ACTION, value.action.name)
            ?.putBoolean(KEY_CAN_DECLINE, value.canDecline)
            ?.putBoolean(KEY_AWAITING_FACE_SETUP, value.awaitingFaceSetup)
            ?.apply()
    }

    private fun readPersisted(prefs: SharedPreferences): InvitationResumeContext? {
        val token = DeepLinkParser.normalizeToken(prefs.getString(KEY_TOKEN, null))
        val code = DeepLinkParser.normalizeCode(prefs.getString(KEY_CODE, null))
        if (token == null && code == null) return null

        val action = runCatching {
            InviteAction.valueOf(prefs.getString(KEY_ACTION, InviteAction.REVIEW.name).orEmpty())
        }.getOrDefault(InviteAction.REVIEW)
        val canDecline = prefs.getBoolean(KEY_CAN_DECLINE, false) &&
            token != null && action == InviteAction.REVIEW

        return InvitationResumeContext(
            token = token,
            code = code,
            action = action,
            canDecline = canDecline,
            awaitingFaceSetup = prefs.getBoolean(KEY_AWAITING_FACE_SETUP, false),
        )
    }
}
