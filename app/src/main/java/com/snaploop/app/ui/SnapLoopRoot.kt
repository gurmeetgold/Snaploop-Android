package com.snaploop.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

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
    when (state.gate) {
        AppGate.MAIN -> SnapLoopMainShell(state = state, coordinator = coordinator)
        AppGate.ONBOARDING -> ParityOnboardingScreen(onCompleted = coordinator::finishOnboarding)
        AppGate.NAME_SETUP -> ParityNameSetupScreen(
            initialName = state.user?.displayName.orEmpty(),
            onSave = coordinator::saveDisplayName,
        )
        else -> SnapLoopApp(activity = activity, coordinator = coordinator)
    }
}
