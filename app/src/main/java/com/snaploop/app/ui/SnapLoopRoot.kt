package com.snaploop.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Single application root. Authentication/onboarding/consent/Face Setup stay in SnapLoopApp;
 * authenticated product navigation is hosted by SnapLoopMainShell so Home / Gallery / You remain
 * persistent across Event screens just as they do on iOS.
 */
@Composable
fun SnapLoopRoot(
    activity: Activity,
    coordinator: AppCoordinator,
) {
    val state by coordinator.state.collectAsState()
    if (state.gate == AppGate.MAIN) {
        SnapLoopMainShell(state = state, coordinator = coordinator)
    } else {
        SnapLoopApp(activity = activity, coordinator = coordinator)
    }
}
