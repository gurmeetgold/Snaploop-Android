package com.snaploop.app.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.snaploop.app.BuildConfig
import com.snaploop.app.core.InviteAction
import com.snaploop.app.core.InvitationResumeStore
import com.snaploop.app.core.JoinIntent
import com.snaploop.app.invite.EventInviteClient
import com.snaploop.app.ui.AppGate
import com.snaploop.app.ui.AppUiState
import com.snaploop.app.ui.AppCoordinator
import kotlinx.coroutines.launch

/**
 * Authenticated push lifecycle mirrored from pinned iOS RootView/PushNotificationClient.
 * Permission is requested at most once on Android 13+, token registration is retried
 * whenever the authenticated user changes, and push payloads are validated through
 * `nextPendingInvite` before an invitation is presented.
 */
@Composable
fun SnapLoopPushLifecycleEffect(
    activity: Activity,
    state: AppUiState,
    coordinator: AppCoordinator,
) {
    val context = activity.applicationContext
    remember(context) {
        SnapLoopPushRefreshStore.initialize(context)
        true
    }
    val pendingRefresh by SnapLoopPushRefreshStore.pending.collectAsState()
    val scope = rememberCoroutineScope()
    val permissionPrefs = remember(context) {
        context.getSharedPreferences(PREFS_PERMISSION, Context.MODE_PRIVATE)
    }
    var processingRefresh by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // iOS registers the FCM token after the authorization attempt regardless
        // of the user's alert choice. Android mirrors that backend-registration
        // behavior; POST_NOTIFICATIONS still controls local presentation.
        scope.launch {
            runCatching { SnapLoopPushTokenRegistrar.registerCurrentToken(context) }
        }
    }

    LaunchedEffect(state.user?.id, state.gate) {
        val authenticatedSetupReady = state.user != null &&
            state.gate != AppGate.RESTORING &&
            state.gate != AppGate.AUTH &&
            state.gate != AppGate.ONBOARDING
        if (!authenticatedSetupReady || !BuildConfig.FIREBASE_CONFIG_PRESENT) return@LaunchedEffect

        SnapLoopNotifications.ensureChannels(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !permissionPrefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
        ) {
            permissionPrefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { SnapLoopPushTokenRegistrar.registerCurrentToken(context) }
        }
    }

    LaunchedEffect(
        pendingRefresh,
        state.gate,
        state.user?.id,
        state.pendingInvite?.id,
        state.busy,
    ) {
        if (
            !pendingRefresh ||
            state.gate != AppGate.MAIN ||
            state.user == null ||
            state.busy ||
            processingRefresh
        ) {
            return@LaunchedEffect
        }

        // Match pinned iOS: do not replace an invitation already being reviewed.
        if (state.pendingInvite != null) {
            SnapLoopPushRefreshStore.clear()
            return@LaunchedEffect
        }

        processingRefresh = true
        try {
            val token = EventInviteClient().nextPendingToken()
            SnapLoopPushRefreshStore.clear()
            if (token == null) {
                InvitationResumeStore.clear()
                coordinator.dismissPendingInvite()
            } else {
                val intent = JoinIntent(token = token, action = InviteAction.REVIEW)
                InvitationResumeStore.capture(intent)
                coordinator.resolveInvitation(code = null, token = token, autoJoin = false)
            }
        } catch (_: Throwable) {
            // Keep the durable wake-up bit set. A later process/session restore can
            // retry without trusting or persisting any raw push payload.
        } finally {
            processingRefresh = false
        }
    }
}

private const val PREFS_PERMISSION = "snaploop.push.permission"
private const val KEY_PERMISSION_REQUESTED = "requested.v1"
