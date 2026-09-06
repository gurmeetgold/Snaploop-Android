package com.snaploop.app.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.snaploop.app.BuildConfig
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.data.FirebaseEventRepository

@Composable
fun SnapLoopDeepLinkEffect(
    uri: Uri?,
    coordinator: AppCoordinator,
    onConsumed: () -> Unit,
) {
    val state by coordinator.state.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri, state.gate) {
        val deepLink = uri ?: return@LaunchedEffect
        if (state.gate != AppGate.MAIN) return@LaunchedEffect

        val allowedHost = if (BuildConfig.DEBUG) "snaploop-dev.web.app" else "getsnaploop.web.app"
        val joinIntent = DeepLinkParser.parse(deepLink, allowedHost)
        if (joinIntent == null) {
            onConsumed()
            return@LaunchedEffect
        }

        try {
            val repository = FirebaseEventRepository()
            val event = when {
                !joinIntent.token.isNullOrBlank() -> repository.resolveInviteToken(joinIntent.token)
                !joinIntent.code.isNullOrBlank() -> repository.resolveJoinCode(joinIntent.code)
                else -> error("Invite is missing an event code or token.")
            }
            if (state.events.none { it.id == event.id }) {
                repository.join(event.id)
            }
            coordinator.openEvent(event)
        } catch (t: Throwable) {
            errorMessage = t.message?.takeIf { it.isNotBlank() }
                ?: "This SnapLoop invitation could not be opened."
        } finally {
            onConsumed()
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("SnapLoop Invitation") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            },
        )
    }
}
