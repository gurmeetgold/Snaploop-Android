package com.snaploop.app.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.core.InvitationActionPolicy
import com.snaploop.app.data.EventInviteClient
import com.snaploop.app.data.FirebaseEventRepository
import com.snaploop.app.model.EventStatus

/**
 * Preserves incoming links through authentication and dispatches explicit invite
 * actions without collapsing decline into the ordinary review path.
 */
@Composable
fun SnapLoopDeepLinkEffect(
    uri: Uri?,
    coordinator: AppCoordinator,
    onConsumed: () -> Unit,
) {
    val state by coordinator.state.collectAsState()
    var retryNonce by remember { mutableIntStateOf(0) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var declinedEventName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri, state.gate, retryNonce) {
        val deepLink = uri ?: return@LaunchedEffect
        if (state.gate != AppGate.MAIN || actionError != null || declinedEventName != null) {
            return@LaunchedEffect
        }

        val joinIntent = DeepLinkParser.parse(deepLink)
        if (joinIntent == null) {
            onConsumed()
            return@LaunchedEffect
        }

        when (InvitationActionPolicy.dispatch(joinIntent.action)) {
            InvitationActionPolicy.Dispatch.REVIEW -> {
                coordinator.resolveInvitation(
                    code = joinIntent.code,
                    token = joinIntent.token,
                    autoJoin = false,
                )
                onConsumed()
            }

            InvitationActionPolicy.Dispatch.ACCEPT -> {
                coordinator.resolveInvitation(
                    code = joinIntent.code,
                    token = joinIntent.token,
                    autoJoin = true,
                )
                onConsumed()
            }

            InvitationActionPolicy.Dispatch.DECLINE -> {
                val token = joinIntent.token
                    ?: error("Only direct token invitations can be declined from a link.")
                try {
                    val repository = FirebaseEventRepository()
                    val event = repository.resolveInviteToken(token)
                    when (event.status) {
                        EventStatus.active -> Unit
                        EventStatus.endedByOrganizer -> error("This Event has ended.")
                        EventStatus.deletedByOrganizer -> error("This Event is no longer available.")
                        EventStatus.expired -> error("This Event has expired.")
                    }

                    val uid = state.user?.id
                    val alreadyMember = state.events.any { it.id == event.id } ||
                        (uid != null && runCatching {
                            repository.members(event.id).any { it.userId == uid }
                        }.getOrDefault(false))
                    if (alreadyMember) {
                        coordinator.openEvent(event)
                        onConsumed()
                        return@LaunchedEffect
                    }

                    EventInviteClient().decline(event.id)
                    coordinator.dismissPendingInvite()
                    declinedEventName = event.name
                    onConsumed()
                } catch (t: Throwable) {
                    actionError = t.message?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "The invitation could not be declined. Please try again."
                }
            }
        }
    }

    declinedEventName?.let { eventName ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Invitation declined") },
            text = { Text("You declined the invitation to $eventName.") },
            confirmButton = {
                TextButton(onClick = { declinedEventName = null }) {
                    Text("Done")
                }
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Couldn't decline invitation") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionError = null
                        retryNonce++
                    },
                ) {
                    Text("Try Again")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        actionError = null
                        onConsumed()
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
