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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.SnapEvent

/** Authorization/display rules mirrored from pinned iOS ParticipantsView.swift blob b0629e0b… . */
internal object ParticipantsParityPolicy {
    fun currentRole(event: SnapEvent, members: List<EventMember>, userId: String?): EventMember.Role? {
        if (userId == null) return null
        if (event.creatorUserId == userId) return EventMember.Role.organizer
        return members.firstOrNull { it.userId == userId }?.role
    }

    fun canInvite(role: EventMember.Role?): Boolean =
        role == EventMember.Role.organizer || role == EventMember.Role.admin

    fun canLeave(role: EventMember.Role?): Boolean = role != EventMember.Role.organizer

    fun canManage(
        currentRole: EventMember.Role?,
        currentUserId: String?,
        member: EventMember,
    ): Boolean {
        if (member.userId == currentUserId || member.role == EventMember.Role.organizer) return false
        return when (currentRole) {
            EventMember.Role.organizer -> true
            EventMember.Role.admin -> member.role == EventMember.Role.participant
            EventMember.Role.participant, null -> false
        }
    }

    fun canChangeRole(
        currentRole: EventMember.Role?,
        currentUserId: String?,
        member: EventMember,
    ): Boolean = currentRole == EventMember.Role.organizer &&
        member.userId != currentUserId &&
        member.role != EventMember.Role.organizer
}

@Composable
internal fun ParityParticipantsScreen(
    state: AppUiState,
    coordinator: AppCoordinator,
    onBack: () -> Unit,
    onInvite: () -> Unit,
) {
    val event = state.selectedEvent ?: return
    val uid = state.user?.id
    val me = state.members.firstOrNull { it.userId == uid }
    val role = ParticipantsParityPolicy.currentRole(event, state.members, uid)
    val hasFaceSetup = state.user?.hasFaceProfile == true
    var confirmLeave by remember(event.id) { mutableStateOf(false) }
    var memberActionUserId by remember(event.id) { mutableStateOf<String?>(null) }
    var memberActionObservedBusy by remember(event.id) { mutableStateOf(false) }
    var shareOpen by remember(event.id) { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE")
    val legacyInviteRoute = onInvite

    LaunchedEffect(state.busy, state.members, state.message, memberActionUserId) {
        if (memberActionUserId == null) return@LaunchedEffect
        if (state.busy) {
            memberActionObservedBusy = true
        } else if (memberActionObservedBusy) {
            memberActionUserId = null
            memberActionObservedBusy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
            }
            Text(
                "Members",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
            )
            IconButton(onClick = { coordinator.openEvent(event) }, enabled = !state.busy) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh members")
            }
        }

        ParityPremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = SnapColors.Coral)
                Text(
                    "Your sharing",
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Share matched pictures from my phone in this Event",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = me?.sharingEnabled == true,
                    onCheckedChange = coordinator::setSharing,
                    enabled = me != null && !state.busy,
                )
            }

            Text(
                if (me?.sharingEnabled == true) {
                    "Matched photos from your phone can be shared with the people they match."
                } else {
                    "Photo sharing from this phone is off for this Event."
                },
                color = if (me?.sharingEnabled == true) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                } else {
                    SnapColors.Coral
                },
                fontSize = 12.sp,
            )

            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show my own matched pictures from this phone in my Gallery",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = state.includeOwnMatches,
                    onCheckedChange = { enabled ->
                        if (!enabled || hasFaceSetup) coordinator.setIncludeOwnMatches(enabled)
                    },
                    enabled = me?.sharingEnabled == true && !state.busy,
                )
            }

            if (me?.sharingEnabled == true && !hasFaceSetup) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(17.dp))
                    Text(
                        "Set up your face to see your own photo matches.",
                        color = SnapColors.Lilac,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            if (ParticipantsParityPolicy.canInvite(role)) {
                Button(
                    onClick = { shareOpen = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Box(
                        Modifier.fillMaxSize().background(SnapGradients.Brand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White)
                            Text("  Invite People", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (role == EventMember.Role.admin) Icons.Filled.Shield else Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "Event controls are on the Event screen.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            if (ParticipantsParityPolicy.canLeave(role)) {
                TextButton(onClick = { confirmLeave = true }, enabled = !state.busy) {
                    Text("Leave Event", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Text("Event Members", fontSize = 21.sp, fontWeight = FontWeight.Black)

        ParityPremiumCard {
            if (state.members.isEmpty()) {
                Text(
                    "No Event members are available.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }

            state.members.forEachIndexed { index, member ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).background(participantsRoleGradient(member.role), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            participantsInitial(member, uid, state.user?.displayName),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            participantsDisplayName(member, uid, state.user?.displayName),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            participantsRoleName(member.role),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                        )
                    }

                    ParticipantsRoleBadge(member.role)

                    if (memberActionUserId == member.userId && state.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else if (ParticipantsParityPolicy.canManage(role, uid, member)) {
                        ParticipantsManagementMenu(
                            currentRole = role,
                            currentUserId = uid,
                            member = member,
                            enabled = !state.busy,
                            onSetRole = { newRole ->
                                memberActionUserId = member.userId
                                memberActionObservedBusy = false
                                coordinator.setMemberRole(member.userId, newRole)
                            },
                            onRemove = {
                                memberActionUserId = member.userId
                                memberActionObservedBusy = false
                                coordinator.removeMember(member.userId)
                            },
                        )
                    }
                }

                if (index != state.members.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this Event?") },
            text = { Text("Your membership will be removed from this Event.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        coordinator.leaveSelectedEvent()
                    },
                ) {
                    Text("Leave Event", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            },
        )
    }

    if (shareOpen) {
        ParityShareEventDialog(
            event = event,
            inviterName = state.user?.displayName,
            canManageInvites = ParticipantsParityPolicy.canInvite(role),
            onDismiss = { shareOpen = false },
        )
    }
}

@Composable
private fun ParticipantsRoleBadge(role: EventMember.Role) {
    when (role) {
        EventMember.Role.organizer -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = SnapColors.Coral, modifier = Modifier.size(14.dp))
            Text(" Organizer", color = SnapColors.Coral, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        EventMember.Role.admin -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = SnapColors.Lilac, modifier = Modifier.size(14.dp))
            Text(" Admin", color = SnapColors.Lilac, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        EventMember.Role.participant -> Unit
    }
}

@Composable
private fun ParticipantsManagementMenu(
    currentRole: EventMember.Role?,
    currentUserId: String?,
    member: EventMember,
    enabled: Boolean,
    onSetRole: (EventMember.Role) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(member.userId) { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Manage ${member.displayName ?: "Event member"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (ParticipantsParityPolicy.canChangeRole(currentRole, currentUserId, member)) {
                if (member.role == EventMember.Role.participant) {
                    DropdownMenuItem(
                        text = { Text("Make Admin") },
                        leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onSetRole(EventMember.Role.admin)
                        },
                    )
                } else if (member.role == EventMember.Role.admin) {
                    DropdownMenuItem(
                        text = { Text("Change to Member") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onSetRole(EventMember.Role.participant)
                        },
                    )
                }
            }

            DropdownMenuItem(
                text = { Text("Remove from Event", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

private fun participantsDisplayName(member: EventMember, currentUserId: String?, currentUserName: String?): String {
    if (member.userId == currentUserId) {
        currentUserName?.trim()?.takeIf { it.isNotEmpty() }?.let { return "$it (You)" }
    }
    return member.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Event member"
}

private fun participantsInitial(member: EventMember, currentUserId: String?, currentUserName: String?): String =
    participantsDisplayName(member, currentUserId, currentUserName)
        .removeSuffix(" (You)")
        .take(1)
        .uppercase()
        .ifEmpty { "•" }

private fun participantsRoleName(role: EventMember.Role): String = when (role) {
    EventMember.Role.organizer -> "Organizer"
    EventMember.Role.admin -> "Admin"
    EventMember.Role.participant -> "Member"
}

private fun participantsRoleGradient(role: EventMember.Role): Brush = when (role) {
    EventMember.Role.organizer -> SnapGradients.Brand
    EventMember.Role.admin -> SnapGradients.Violet
    EventMember.Role.participant -> SnapGradients.Social
}
