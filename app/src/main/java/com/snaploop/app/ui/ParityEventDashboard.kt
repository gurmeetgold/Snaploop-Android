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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.data.FirebaseMatchRepository
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Event dashboard contract mirrored from the pinned iOS
 * Sources/Features/Events/EventDashboardView.swift blob 81637ef… .
 *
 * Keeping the lifecycle and display policy pure makes the cross-platform behavior
 * testable without Compose and prevents the UI from silently drifting back to
 * server-status-only checks.
 */
internal object EventDashboardParityPolicy {
    enum class Lifecycle { UPCOMING, ACTIVE, GRACE, EXPIRED }
    enum class SyncDisplay { AUTOMATIC, SHARING_OFF, NEEDS_PHOTO_ACCESS, PAUSED }

    val defaultGracePeriodDays: Int = RemoteConfigValues().eventGracePeriodDays

    fun lifecycle(
        event: SnapEvent,
        now: Instant,
        gracePeriodDays: Int = defaultGracePeriodDays,
    ): Lifecycle {
        if (event.status != EventStatus.active) return Lifecycle.EXPIRED
        if (now.isBefore(event.startsAt)) return Lifecycle.UPCOMING
        if (!now.isAfter(event.endsAt)) return Lifecycle.ACTIVE

        val zone = eventZone(event)
        val graceEnd = event.endsAt
            .atZone(zone)
            .plusDays(gracePeriodDays.coerceAtLeast(0).toLong())
            .toInstant()
        return if (!now.isAfter(graceEnd)) Lifecycle.GRACE else Lifecycle.EXPIRED
    }

    fun canSync(
        event: SnapEvent,
        now: Instant,
        gracePeriodDays: Int = defaultGracePeriodDays,
    ): Boolean = when (lifecycle(event, now, gracePeriodDays)) {
        Lifecycle.ACTIVE, Lifecycle.GRACE -> true
        Lifecycle.UPCOMING, Lifecycle.EXPIRED -> false
    }

    fun syncDisplay(
        canSync: Boolean,
        canReadPhotos: Boolean,
        hasUser: Boolean,
        sharingEnabled: Boolean,
    ): SyncDisplay = when {
        !canSync -> SyncDisplay.PAUSED
        !canReadPhotos -> SyncDisplay.NEEDS_PHOTO_ACCESS
        !hasUser -> SyncDisplay.PAUSED
        sharingEnabled -> SyncDisplay.AUTOMATIC
        else -> SyncDisplay.SHARING_OFF
    }

    fun photosOfMeCount(
        ownerUserIds: List<String>,
        userId: String,
        sharingEnabled: Boolean,
    ): Int = ownerUserIds.count { sharingEnabled || it != userId }

    private fun eventZone(event: SnapEvent): ZoneId = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
}

@Composable
internal fun ParityEventDashboard(
    state: AppUiState,
    coordinator: AppCoordinator,
    onBack: () -> Unit,
    onPhotos: () -> Unit,
    onScan: () -> Unit,
    onMembers: () -> Unit,
    onInvite: () -> Unit,
    onEdit: () -> Unit,
) {
    val event = state.selectedEvent ?: return
    val uid = state.user?.id
    val role = parityCurrentRole(event, state.members, uid)
    val me = state.members.firstOrNull { it.userId == uid }
    val now = Instant.now()
    val lifecycle = EventDashboardParityPolicy.lifecycle(event, now)
    val canSync = EventDashboardParityPolicy.canSync(event, now)
    val canManageMembers = role == EventMember.Role.organizer || role == EventMember.Role.admin
    val canManageEvent = canManageMembers
    val isOrganizer = role == EventMember.Role.organizer

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var photoAccess by remember { mutableStateOf(PhotoAccessParity.state(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
                photoAccess = PhotoAccessParity.state(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canReadPhotos = photoAccess == PhotoAccessState.AUTHORIZED || photoAccess == PhotoAccessState.LIMITED
    val syncDisplay = EventDashboardParityPolicy.syncDisplay(
        canSync = canSync,
        canReadPhotos = canReadPhotos,
        hasUser = uid != null,
        sharingEnabled = me?.sharingEnabled == true,
    )

    val photosOfMe by produceState(
        initialValue = state.photos.size,
        key1 = event.id,
        key2 = uid,
        key3 = Pair(me?.sharingEnabled, state.photos),
    ) {
        if (uid == null) {
            value = 0
            return@produceState
        }
        value = runCatching {
            val matches = FirebaseMatchRepository().myPhotos(event.id, uid)
            EventDashboardParityPolicy.photosOfMeCount(
                ownerUserIds = matches.map { it.ownerUserId },
                userId = uid,
                sharingEnabled = me?.sharingEnabled == true,
            )
        }.getOrDefault(state.photos.size)
    }

    var confirmEnd by remember(event.id) { mutableStateOf(false) }
    var confirmDelete by remember(event.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                Text("Events")
            }
        }

        ParityEventHero(event = event, role = role, lifecycle = lifecycle)

        if (event.status == EventStatus.deletedByOrganizer) {
            ParityPremiumCard(Modifier.padding(horizontal = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                    Text(
                        "This Event is in Deleted.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (isOrganizer) {
                ParityEventManagerControls(
                    event = event,
                    role = role,
                    busy = state.busy,
                    onEdit = onEdit,
                    onEnd = { confirmEnd = true },
                    onReopen = coordinator::reopenSelectedEvent,
                    onMoveToDeleted = { confirmDelete = true },
                    onRestore = coordinator::restoreSelectedEvent,
                )
            }
        } else {
            ParityEventSyncRow(syncDisplay)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ParityEventFeatureTile(
                    title = "My Photos",
                    subtitle = "$photosOfMe found of you",
                    icon = Icons.Filled.Image,
                    gradient = SnapGradients.Brand,
                    modifier = Modifier.weight(1f),
                    onClick = onPhotos,
                )
                ParityEventFeatureTile(
                    title = "Scan Photos",
                    subtitle = "Check New Event Photos",
                    icon = Icons.Filled.PhotoLibrary,
                    gradient = SnapGradients.Social,
                    modifier = Modifier.weight(1f),
                    enabled = canSync,
                    onClick = onScan,
                )
            }

            ParityEventMembersRow(
                members = state.members,
                currentUserId = uid,
                currentUserName = state.user?.displayName,
                onClick = onMembers,
            )

            if (event.status == EventStatus.active && canManageMembers) {
                Card(
                    onClick = onInvite,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).background(SnapColors.Coral.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = SnapColors.Coral)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Invite People", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Share code, link, QR or phone invite",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                fontSize = 13.sp,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f))
                    }
                }
            }

            if (canManageEvent) {
                ParityEventManagerControls(
                    event = event,
                    role = role,
                    busy = state.busy,
                    onEdit = onEdit,
                    onEnd = { confirmEnd = true },
                    onReopen = coordinator::reopenSelectedEvent,
                    onMoveToDeleted = { confirmDelete = true },
                    onRestore = coordinator::restoreSelectedEvent,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this Event?") },
            text = { Text("New joins and photo scans will stop.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    coordinator.endSelectedEvent()
                }) {
                    Text("End Event", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move this Event to Deleted?") },
            text = { Text("The Event will move to Deleted and can be restored while it is still retained.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    coordinator.moveSelectedEventToDeleted()
                }) {
                    Text("Move to Deleted", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ParityEventHero(
    event: SnapEvent,
    role: EventMember.Role?,
    lifecycle: EventDashboardParityPolicy.Lifecycle,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(230.dp)
            .background(SnapGradients.Brand, RoundedCornerShape(30.dp))
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(52.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(parityCategoryIcon(event.category), contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.weight(1f))
            Surface(color = Color.White.copy(alpha = 0.22f), shape = CircleShape) {
                Text(
                    parityEventStatusText(event, lifecycle),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                event.name,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(17.dp))
                Text(
                    parityEventRange(event),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                role?.let {
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(parityRoleIcon(it), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text(
                                parityRoleLabel(it),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParityEventSyncRow(display: EventDashboardParityPolicy.SyncDisplay) {
    val presentation = when (display) {
        EventDashboardParityPolicy.SyncDisplay.AUTOMATIC -> SyncPresentation(
            title = "Automatic",
            detail = "SnapLoop automatically checks this Event for new photos",
            icon = Icons.Filled.Cloud,
            tint = SnapColors.Mint,
        )
        EventDashboardParityPolicy.SyncDisplay.SHARING_OFF -> SyncPresentation(
            title = "Sharing off",
            detail = "Photo sharing is turned off for this Event",
            icon = Icons.Filled.PauseCircle,
            tint = SnapColors.Amber,
        )
        EventDashboardParityPolicy.SyncDisplay.NEEDS_PHOTO_ACCESS -> SyncPresentation(
            title = "Needs access",
            detail = "Allow Photos access to check this Event",
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.error,
        )
        EventDashboardParityPolicy.SyncDisplay.PAUSED -> SyncPresentation(
            title = "Paused",
            detail = "Photo scanning is paused for this Event",
            icon = Icons.Filled.PauseCircle,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }

    ParityPremiumCard(Modifier.padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(presentation.tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(presentation.icon, contentDescription = null, tint = presentation.tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Photo Scan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    presentation.detail,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                )
            }
            Text(
                presentation.title,
                color = presentation.tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private data class SyncPresentation(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
private fun ParityEventFeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.52f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxWidth().height(154.dp).background(gradient).padding(16.dp)) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ParityEventMembersRow(
    members: List<EventMember>,
    currentUserId: String?,
    currentUserName: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = SnapColors.Lilac)
                Text(
                    "Event Members",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Text("View all", color = SnapColors.Coral, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                members.take(6).forEach { member ->
                    val initial = when {
                        member.userId == currentUserId && !currentUserName.isNullOrBlank() -> currentUserName.trim().take(1)
                        !member.displayName.isNullOrBlank() -> member.displayName.trim().take(1)
                        else -> "•"
                    }.uppercase(Locale.getDefault())
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(parityMemberGradient(member.role), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initial, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParityEventManagerControls(
    event: SnapEvent,
    role: EventMember.Role?,
    busy: Boolean,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
    onReopen: () -> Unit,
    onMoveToDeleted: () -> Unit,
    onRestore: () -> Unit,
) {
    ParityPremiumCard(Modifier.padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (role == EventMember.Role.admin) Icons.Filled.Shield else Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = SnapColors.Coral,
            )
            Text(
                if (role == EventMember.Role.admin) "Admin Controls" else "Organizer Controls",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 7.dp),
            )
        }

        if (event.status == EventStatus.active) {
            TextButton(onClick = onEdit, enabled = !busy) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Text("  Edit Event")
            }
            TextButton(onClick = onEnd, enabled = !busy) {
                Icon(Icons.Filled.StopCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  End Event", color = MaterialTheme.colorScheme.error)
            }
        }

        if (role == EventMember.Role.organizer) {
            if (event.status == EventStatus.endedByOrganizer) {
                TextButton(onClick = onReopen, enabled = !busy) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Text("  Reopen Event")
                }
            }
            if (event.status == EventStatus.deletedByOrganizer) {
                TextButton(onClick = onRestore, enabled = !busy) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Text("  Restore Event")
                }
            } else {
                TextButton(onClick = onMoveToDeleted, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  Move to Deleted", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun parityCurrentRole(
    event: SnapEvent,
    members: List<EventMember>,
    uid: String?,
): EventMember.Role? {
    if (uid == null) return null
    if (event.creatorUserId == uid) return EventMember.Role.organizer
    return members.firstOrNull { it.userId == uid }?.role
}

private fun parityRoleLabel(role: EventMember.Role): String = when (role) {
    EventMember.Role.organizer -> "ORGANIZER"
    EventMember.Role.admin -> "ADMIN"
    EventMember.Role.participant -> "MEMBER"
}

private fun parityRoleIcon(role: EventMember.Role): ImageVector = when (role) {
    EventMember.Role.organizer -> Icons.Filled.WorkspacePremium
    EventMember.Role.admin -> Icons.Filled.Shield
    EventMember.Role.participant -> Icons.Filled.Person
}

private fun parityCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.LocationOn
    EventCategory.wedding -> Icons.Filled.Image
    EventCategory.party -> Icons.Filled.Groups
    EventCategory.birthday -> Icons.Filled.MoreHoriz
    EventCategory.conference -> Icons.Filled.Groups
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.MoreHoriz
    EventCategory.other -> Icons.Filled.PhotoLibrary
}

private fun parityMemberGradient(role: EventMember.Role): Brush = when (role) {
    EventMember.Role.organizer -> SnapGradients.Brand
    EventMember.Role.admin -> SnapGradients.Violet
    EventMember.Role.participant -> SnapGradients.Social
}

private fun parityEventStatusText(
    event: SnapEvent,
    lifecycle: EventDashboardParityPolicy.Lifecycle,
): String = when (event.status) {
    EventStatus.endedByOrganizer -> "ENDED"
    EventStatus.deletedByOrganizer -> "DELETED"
    EventStatus.expired -> "COMPLETED"
    EventStatus.active -> when (lifecycle) {
        EventDashboardParityPolicy.Lifecycle.UPCOMING -> "UPCOMING"
        EventDashboardParityPolicy.Lifecycle.ACTIVE -> "LIVE"
        EventDashboardParityPolicy.Lifecycle.GRACE -> "PHOTO WINDOW"
        EventDashboardParityPolicy.Lifecycle.EXPIRED -> "COMPLETED"
    }
}

private fun parityEventRange(event: SnapEvent): String {
    val zone = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val start = event.startsAt.atZone(zone).toLocalDate().format(formatter)
    val end = event.endsAt.atZone(zone).toLocalDate().format(formatter)
    return if (start == end) start else "$start – $end"
}
