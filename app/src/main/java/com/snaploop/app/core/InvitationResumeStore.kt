package com.snaploop.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local invitation navigation context.
 *
 * AppCoordinator already preserves the resolved Event while Face Setup is open,
 * but successful enrollment re-runs authenticated routing and intentionally
 * rebuilds AppUiState. This store keeps only the non-sensitive routing intent
 * needed to replay the invitation afterward; no face or photo data is stored.
 */
data class InvitationResumeContext(
    val token: String? = null,
    val code: String? = null,
    val action: InviteAction = InviteAction.REVIEW,
    val canDecline: Boolean = false,
    val awaitingFaceSetup: Boolean = false,
)

object InvitationResumeStore {
    private val _state = MutableStateFlow<InvitationResumeContext?>(null)
    val state: StateFlow<InvitationResumeContext?> = _state.asStateFlow()

    fun capture(intent: JoinIntent) {
        _state.value = InvitationResumeContext(
            token = intent.token,
            code = intent.code,
            action = intent.action,
            canDecline = intent.action == InviteAction.REVIEW && !intent.token.isNullOrBlank(),
            awaitingFaceSetup = false,
        )
    }

    fun beginFaceSetup(
        fallbackToken: String,
        action: InviteAction = _state.value?.action ?: InviteAction.REVIEW,
    ) {
        val current = _state.value
        _state.value = InvitationResumeContext(
            token = current?.token ?: fallbackToken,
            code = current?.code,
            action = action,
            canDecline = current?.canDecline == true,
            awaitingFaceSetup = true,
        )
    }

    fun markFaceSetupResumed() {
        _state.value = _state.value?.copy(awaitingFaceSetup = false)
    }

    fun clear() {
        _state.value = null
    }
}
