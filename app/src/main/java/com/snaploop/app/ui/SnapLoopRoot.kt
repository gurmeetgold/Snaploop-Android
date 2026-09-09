package com.snaploop.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snaploop.app.core.InviteAction
import com.snaploop.app.core.InvitationResumeStore
import com.snaploop.app.notifications.SnapLoopPushLifecycleEffect
import com.snaploop.app.scanner.AutomaticForegroundScanController

/**
 * Single application root. New parity surfaces are intercepted here so the
 * production flows can replace legacy private Composables incrementally without
 * destabilizing authentication or the authenticated shell.
 */
@Composable
fun SnapLoopRoot(
    activity: Activity,
    coordinator: AppCoordinator,
) {
    val state by coordinator.state.collectAsState()
    val invitationResume by InvitationResumeStore.state.collectAsState()
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val automaticScanner = remember(context) { AutomaticForegroundScanController(context) }
    val uiPrefs = remember(context) { context.getSharedPreferences("snaploop.ui", 0) }
    var preAuthOnboardingCompleted by remember(context) {
        mutableStateOf(
            PreAuthOnboardingParity.hasCompleted(
                globalCompleted = uiPrefs.getBoolean(PreAuthOnboardingParity.GLOBAL_KEY, false),
                legacyEntries = uiPrefs.all,
            ),
        )
    }
    val scanTriggerGeneration = "${state.selectedEvent?.id.orEmpty()}:own=${state.includeOwnMatches}"
    var resumeResolutionInFlight by remember { mutableStateOf(false) }

    // Pinned iOS onboarding is device-level and occurs before authentication. Migrate any
    // previously completed account-scoped Android onboarding state to the new global key.
    LaunchedEffect(preAuthOnboardingCompleted) {
        if (
            preAuthOnboardingCompleted &&
            !uiPrefs.getBoolean(PreAuthOnboardingParity.GLOBAL_KEY, false)
        ) {
            uiPrefs.edit().putBoolean(PreAuthOnboardingParity.GLOBAL_KEY, true).apply()
        }
    }

    // The coordinator still maintains its legacy per-account onboarding gate. After a user who
    // completed the new pre-auth flow signs in, consume that internal gate immediately so they do
    // not see onboarding twice. This also preserves compatibility with existing coordinator logic.
    LaunchedEffect(state.gate, state.user?.id, preAuthOnboardingCompleted) {
        if (
            preAuthOnboardingCompleted &&
            state.gate == AppGate.ONBOARDING &&
            state.user != null
        ) {
            coordinator.finishOnboarding()
        }
    }

    SnapLoopPushLifecycleEffect(
        activity = activity,
        state = state,
        coordinator = coordinator,
    )

    // Rebuild an interrupted invitation after Android process recreation, or
    // continue the current-process route after successful Face Setup. Restored
    // context is one-shot so it cannot contaminate a later unrelated invite.
    LaunchedEffect(
        state.gate,
        state.busy,
        state.message,
        state.user?.hasFaceProfile,
        state.pendingInvite?.id,
        state.selectedEvent?.id,
        invitationResume,
    ) {
        if (
            state.gate != AppGate.MAIN ||
            state.busy ||
            state.message != null ||
            state.pendingInvite != null ||
            state.selectedEvent != null ||
            resumeResolutionInFlight
        ) {
            return@LaunchedEffect
        }

        val restored = InvitationResumeStore.takeRestoredForResolution()
        val faceSetupResume = invitationResume?.takeIf {
            restored == null && it.awaitingFaceSetup && state.user?.hasFaceProfile == true
        }
        val resume = restored ?: faceSetupResume ?: return@LaunchedEffect

        if (resume.awaitingFaceSetup && state.user?.hasFaceProfile == true) {
            InvitationResumeStore.markFaceSetupResumed()
        }
        resumeResolutionInFlight = true
        coordinator.resolveInvitation(
            code = resume.code,
            token = resume.token,
            autoJoin = resume.action == InviteAction.ACCEPT && state.user?.hasFaceProfile == true,
        )
    }

    // Coordinator invitation work is asynchronous. On success the Event/review
    // state becomes visible. On failure keep the durable context and re-arm it;
    // dismissing the error then performs the same safe resolution again.
    LaunchedEffect(
        state.busy,
        state.message,
        state.pendingInvite?.id,
        state.selectedEvent?.id,
        resumeResolutionInFlight,
    ) {
        if (!resumeResolutionInFlight || state.busy) return@LaunchedEffect
        when {
            state.pendingInvite != null || state.selectedEvent != null -> {
                resumeResolutionInFlight = false
            }
            state.message != null -> {
                InvitationResumeStore.rearmRestoredForResolution()
                resumeResolutionInFlight = false
            }
        }
    }

    // Once a resumed/direct invitation has successfully opened the Event, its
    // routing context is no longer needed. Review contexts stay alive while the
    // invitation card is visible so token invitations retain Decline semantics.
    LaunchedEffect(state.pendingInvite?.id, state.selectedEvent?.id, invitationResume?.awaitingFaceSetup) {
        val resume = invitationResume ?: return@LaunchedEffect
        if (
            state.gate == AppGate.MAIN &&
            !resume.awaitingFaceSetup &&
            state.pendingInvite == null &&
            state.selectedEvent != null
        ) {
            InvitationResumeStore.clear()
        }
    }

    // Authenticated startup, Face Setup completion, Event membership changes and
    // own-match preference changes all get an immediate foreground opportunity.
    // The controller's persisted fingerprint still prevents unnecessary rescans.
    LaunchedEffect(state.gate, state.events, scanTriggerGeneration) {
        if (
            state.gate == AppGate.MAIN &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            automaticScanner.request(
                events = state.events,
                triggerGeneration = scanTriggerGeneration,
            )
        }
    }

    // Foreground return triggers another eligibility check. Moving to the
    // background cancels the active bounded pass; encrypted checkpoints make
    // the next foreground pass resume safely rather than starting over.
    DisposableEffect(lifecycleOwner, automaticScanner, coordinator) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val current = coordinator.state.value
                    if (current.gate == AppGate.MAIN) {
                        automaticScanner.request(
                            events = current.events,
                            triggerGeneration = "${current.selectedEvent?.id.orEmpty()}:own=${current.includeOwnMatches}",
                        )
                    }
                }
                Lifecycle.Event.ON_STOP -> automaticScanner.cancelActive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            automaticScanner.cancelActive()
        }
    }

    DisposableEffect(automaticScanner) {
        onDispose { automaticScanner.close() }
    }

    if (state.gate == AppGate.AUTH && !preAuthOnboardingCompleted) {
        ParityOnboardingScreen(
            onCompleted = {
                uiPrefs.edit().putBoolean(PreAuthOnboardingParity.GLOBAL_KEY, true).apply()
                preAuthOnboardingCompleted = true
            },
        )
        return
    }

    when (state.gate) {
        AppGate.AUTH -> ParityAuthScreen(
            activity = activity,
            state = state,
            coordinator = coordinator,
        )
        AppGate.MAIN -> {
            if (state.pendingInvite != null) {
                ParityInvitationReviewScreen(state = state, coordinator = coordinator)
            } else {
                SnapLoopMainShell(state = state, coordinator = coordinator)
            }
        }
        AppGate.ONBOARDING -> ParityOnboardingScreen(onCompleted = coordinator::finishOnboarding)
        AppGate.NAME_SETUP -> ParityNameSetupScreen(
            initialName = state.user?.displayName.orEmpty(),
            onSave = coordinator::saveDisplayName,
        )
        AppGate.FACE_SETUP -> ParityFaceSetupScreen(
            state = state,
            onCapture = coordinator::addFaceCapture,
            onReset = coordinator::resetFaceCaptures,
            onComplete = coordinator::completeFaceSetup,
            onExit = coordinator::cancelFaceSetup,
            // iOS keeps biometric consent active when only Face Setup is deleted.
            // Consent withdrawal remains a separate Privacy action.
            onDelete = coordinator::deleteFaceSetupPreservingConsent,
        )
        else -> SnapLoopApp(activity = activity, coordinator = coordinator)
    }
}
