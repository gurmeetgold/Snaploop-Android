package com.snaploop.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable

/**
 * Single application root. Face Setup is implemented by SnapLoopApp via
 * GuidedFaceEnrollmentCamera, avoiding duplicate camera state machines.
 */
@Composable
fun SnapLoopRoot(
    activity: Activity,
    coordinator: AppCoordinator,
) {
    SnapLoopApp(activity = activity, coordinator = coordinator)
}
