package com.snaploop.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.core.InviteAction

/**
 * Preserves incoming links through authentication and hands them to the same
 * coordinator-backed invitation review flow used by manual code/link/QR entry.
 */
@Composable
fun SnapLoopDeepLinkEffect(
    uri: Uri?,
    coordinator: AppCoordinator,
    onConsumed: () -> Unit,
) {
    val state by coordinator.state.collectAsState()

    LaunchedEffect(uri, state.gate) {
        val deepLink = uri ?: return@LaunchedEffect
        if (state.gate != AppGate.MAIN) return@LaunchedEffect

        val joinIntent = DeepLinkParser.parse(deepLink)
        if (joinIntent == null) {
            onConsumed()
            return@LaunchedEffect
        }

        coordinator.resolveInvitation(
            code = joinIntent.code,
            token = joinIntent.token,
            autoJoin = joinIntent.action == InviteAction.ACCEPT,
        )
        onConsumed()
    }
}
