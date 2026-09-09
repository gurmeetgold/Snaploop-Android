package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaploop.app.core.InviteAction
import com.snaploop.app.core.InvitationResumeStore
import com.snaploop.app.data.FirebaseEventRepository
import com.snaploop.app.invite.EventInviteClient
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.SnapEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Invitation UI mirrored from pinned iOS JoinEventView.swift. */
@Composable
internal fun ParityInvitationReviewScreen(
    state: AppUiState,
    coordinator: AppCoordinator,
) {
    val event = state.pendingInvite ?: return
    val resumeContext by InvitationResumeStore.state.collectAsState()
    val action = resumeContext?.action ?: InviteAction.REVIEW
    val hasFaceSetup = state.user?.hasFaceProfile == true
    val scope = rememberCoroutineScope()

    var participantCount by remember(event.id) { mutableIntStateOf(0) }
    var inviterLabel by remember(event.id) { mutableStateOf<String?>(null) }
    var isDeclining by remember(event.id) { mutableStateOf(false) }
    var declineError by remember(event.id) { mutableStateOf<String?>(null) }
    var declined by remember(event.id) { mutableStateOf(false) }

    LaunchedEffect(event.id, resumeContext?.token, resumeContext?.code) {
        participantCount = runCatching { FirebaseEventRepository().members(event.id).size }.getOrDefault(0)
        inviterLabel = runCatching {
            val client = EventInviteClient()
            when {
                !resumeContext?.token.isNullOrBlank() -> client.previewToken(resumeContext!!.token!!).inviterName
                !resumeContext?.code.isNullOrBlank() -> client.previewCode(resumeContext!!.code!!).inviterName
                else -> client.previewCode(event.joinCode).inviterName
            }
        }.getOrNull()
    }

    ParityBrandBackground {
        if (declined) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ParityPremiumCard {
                    Text(
                        "Invitation declined",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "You declined the invitation to ${event.name}.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                    ParityPrimaryButton(
                        text = "Done",
                        onClick = {
                            InvitationResumeStore.clear()
                            coordinator.dismissPendingInvite()
                        },
                    )
                }
            }
            return@ParityBrandBackground
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        InvitationResumeStore.clear()
                        coordinator.dismissPendingInvite()
                    },
                    enabled = !state.busy && !isDeclining,
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                }
                Text(
                    "Invitation",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.size(48.dp))
            }

            Box(
                Modifier.size(76.dp).background(SnapColors.BlueSoft.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    invitationCategoryIcon(event.category),
                    contentDescription = null,
                    tint = SnapColors.Lilac,
                    modifier = Modifier.size(36.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "You're invited to",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    event.name,
                    textAlign = TextAlign.Center,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                inviterLabel?.let { inviter ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(18.dp))
                        Text(
                            "  Invited by $inviter",
                            color = SnapColors.Lilac,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            ParityPremiumCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = SnapColors.Coral)
                    Text(
                        "  ${invitationDateRange(event)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "SnapLoop scans only photos taken during these Event dates.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                )
                if (participantCount > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "  $participantCount members",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        )
                    }
                }
            }

            Text(
                "Join this Event to get your photos found on other participating members’ phones.",
                modifier = Modifier.padding(horizontal = 8.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            )

            when {
                !hasFaceSetup -> {
                    ParityPrimaryButton(
                        text = "Set Up My Face",
                        enabled = !state.busy && !isDeclining,
                        onClick = {
                            InvitationResumeStore.beginFaceSetup(
                                fallbackToken = event.inviteToken,
                                action = action,
                            )
                            coordinator.openFaceSetupFromMain()
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Face, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(17.dp))
                        Text(
                            "  Face Setup is needed before you can join and receive matched photos.",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                        )
                    }
                }

                action == InviteAction.REVIEW -> {
                    ParityPrimaryButton(
                        text = if (state.busy) "Joining…" else "Join Event",
                        enabled = !state.busy && !isDeclining,
                        onClick = coordinator::confirmPendingInvite,
                    )
                }

                else -> {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            if (action == InviteAction.ACCEPT) "  Joining Event…" else "  Opening invitation…",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            declineError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (resumeContext?.canDecline == true && action == InviteAction.REVIEW) {
                TextButton(
                    onClick = {
                        if (isDeclining || state.busy) return@TextButton
                        isDeclining = true
                        declineError = null
                        scope.launch {
                            runCatching { EventInviteClient().decline(event.id) }
                                .onSuccess {
                                    isDeclining = false
                                    declined = true
                                }
                                .onFailure { error ->
                                    isDeclining = false
                                    declineError = error.message?.trim()?.takeIf { it.isNotEmpty() }
                                        ?: "The invitation could not be declined. Please try again."
                                }
                        }
                    },
                    enabled = !state.busy && !isDeclining,
                ) {
                    if (isDeclining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        if (isDeclining) "  Declining…" else "Decline",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun invitationCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.LocationOn
    EventCategory.wedding -> Icons.Filled.PhotoLibrary
    EventCategory.party -> Icons.Filled.Groups
    EventCategory.birthday -> Icons.Filled.Cake
    EventCategory.conference -> Icons.Filled.Work
    EventCategory.family -> Icons.Filled.FamilyRestroom
    EventCategory.sports -> Icons.Filled.SportsSoccer
    EventCategory.other -> Icons.Filled.PhotoLibrary
}

private fun invitationDateRange(event: SnapEvent): String {
    val zone = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val start = event.startsAt.atZone(zone).toLocalDate().format(formatter)
    val end = event.endsAt.atZone(zone).toLocalDate().format(formatter)
    return if (start == end) start else "$start – $end"
}
