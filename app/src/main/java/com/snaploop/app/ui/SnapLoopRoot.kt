package com.snaploop.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val automaticScanner = remember(context) { AutomaticForegroundScanController(context) }

    // Authenticated startup, Face Setup completion and Event membership changes
    // all flow through MAIN with a refreshed Event snapshot. Requesting here
    // gives newly joined live Events the same immediate opportunity as iOS.
    LaunchedEffect(state.gate, state.events) {
        if (
            state.gate == AppGate.MAIN &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            automaticScanner.request(state.events)
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
                    if (current.gate == AppGate.MAIN) automaticScanner.request(current.events)
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

    when (state.gate) {
        AppGate.MAIN -> SnapLoopMainShell(state = state, coordinator = coordinator)
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
            // The existing server-authoritative withdrawal path erases the
            // face profile, encrypted local reference and own-match opt-ins.
            // A later fresh setup therefore requires active consent again.
            onDelete = coordinator::withdrawBiometrics,
        )
        else -> SnapLoopApp(activity = activity, coordinator = coordinator)
    }
}
