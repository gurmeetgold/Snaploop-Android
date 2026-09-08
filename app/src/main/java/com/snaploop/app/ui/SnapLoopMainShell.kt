package com.snaploop.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.data.FirebaseEventRepository
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class ShellEventPage { DASHBOARD, PHOTOS, SCAN, MEMBERS, INVITE, PHONE_INVITE, EDIT }

/**
 * The authenticated product shell. Unlike the original Android prototype, the main tab bar lives
 * above the Event navigation stack, matching iOS: Home / Gallery / You remain available throughout
 * normal Event navigation instead of disappearing as soon as an Event is opened.
 */
@Composable
internal fun SnapLoopMainShell(
    state: AppUiState,
    coordinator: AppCoordinator,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color.White.copy(alpha = 0.98f)) {
                ShellTabItem(
                    selected = tab == 0,
                    icon = Icons.Filled.Home,
                    label = "Home",
                ) {
                    coordinator.dismissPendingInvite()
                    coordinator.closeEvent()
                    tab = 0
                }
                ShellTabItem(
                    selected = tab == 1,
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Gallery",
                ) {
                    coordinator.dismissPendingInvite()
                    coordinator.closeEvent()
                    tab = 1
                }
                ShellTabItem(
                    selected = tab == 2,
                    icon = Icons.Filled.Person,
                    label = "You",
                ) {
                    coordinator.dismissPendingInvite()
                    coordinator.closeEvent()
                    tab = 2
                }
            }
        },
    ) { inset ->
        ShellBackground(Modifier.padding(inset)) {
            when {
                state.pendingInvite != null -> ShellInvitationReview(state.pendingInvite, coordinator)
                state.selectedEvent != null -> ShellEventHost(state, coordinator)
                tab == 0 -> ShellHome(state, coordinator)
                tab == 1 -> ShellGallery(state, coordinator)
                else -> ShellYou(state, coordinator)
            }
        }
    }

    if (state.busy && state.scanProgress == null && state.scanResult == null) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(shape = RoundedCornerShape(24.dp)) {
                CircularProgressIndicator(Modifier.padding(28.dp))
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = coordinator::clearMessage,
            confirmButton = { TextButton(onClick = coordinator::clearMessage) { Text("OK") } },
            title = { Text("SnapLoop") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun ShellTabItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
    )
}

@Composable
private fun ShellHome(state: AppUiState, coordinator: AppCoordinator) {
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var joinOpen by rememberSaveable { mutableStateOf(false) }
    val uid = state.user?.id
    val roles by shellEventRoles(uid, state.events)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Hi, ${state.user?.displayName ?: "there"} 👋",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = ShellColors.Ink,
                )
                Text(
                    "Photos your friends took of you on their phones, brought to your phone automatically.",
                    color = ShellColors.Secondary,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            IconButton(onClick = coordinator::refreshEvents) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Events", tint = ShellColors.Coral)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShellActionCard(
                icon = Icons.Filled.Add,
                title = "Create Event",
                subtitle = "Trip, party, family & more",
                modifier = Modifier.weight(1f),
            ) { createOpen = true }
            ShellActionCard(
                icon = Icons.Filled.Groups,
                title = "Join Event",
                subtitle = "Code, link or QR",
                modifier = Modifier.weight(1f),
            ) { joinOpen = true }
        }

        ShellSectionTitle("Your Events")
        val visible = state.events.filter { it.status != EventStatus.deletedByOrganizer }
        if (visible.isEmpty()) {
            ShellCard(Modifier.padding(horizontal = 18.dp)) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = ShellColors.Lilac,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(46.dp),
                )
                Text(
                    "No Events yet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    "Create an Event, or join one with a code, link or QR.",
                    textAlign = TextAlign.Center,
                    color = ShellColors.Secondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        } else {
            visible.forEach { event ->
                ShellEventCard(
                    event = event,
                    role = roles[event.id] ?: if (event.creatorUserId == uid) EventMember.Role.organizer else null,
                    modifier = Modifier.padding(horizontal = 18.dp),
                ) { coordinator.openEvent(event) }
            }
        }

        val deleted = state.events.filter { it.status == EventStatus.deletedByOrganizer }
        if (deleted.isNotEmpty()) {
            ShellSectionTitle("Deleted")
            deleted.forEach { event ->
                ShellEventCard(event, roles[event.id], Modifier.padding(horizontal = 18.dp)) {
                    coordinator.openEvent(event)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (createOpen) {
        ShellEventFormDialog(
            title = "Create an Event",
            initialName = "",
            initialCategory = EventCategory.trip,
            initialLocation = "",
            initialStart = LocalDate.now(),
            initialEnd = LocalDate.now().plusDays(3),
            submitLabel = "Create Event",
            onDismiss = { createOpen = false },
            onSubmit = { name, category, location, start, end ->
                if (state.user?.hasFaceProfile == true) {
                    createOpen = false
                    coordinator.createEvent(name, category, location, start, end)
                } else {
                    createOpen = false
                    coordinator.openFaceSetupFromMain()
                }
            },
        )
    }

    if (joinOpen) {
        ShellJoinDialog(
            onDismiss = { joinOpen = false },
            onResolve = {
                joinOpen = false
                coordinator.resolveJoinInput(it)
            },
        )
    }
}

@Composable
private fun shellEventRoles(uid: String?, events: List<SnapEvent>) = produceState<Map<String, EventMember.Role>>(
    initialValue = emptyMap(),
    key1 = uid,
    key2 = events.map { "${it.id}:${it.updatedAt}" },
) {
    if (uid == null) {
        value = emptyMap()
        return@produceState
    }
    val repository = FirebaseEventRepository()
    val resolved = linkedMapOf<String, EventMember.Role>()
    events.forEach { event ->
        if (event.creatorUserId == uid) {
            resolved[event.id] = EventMember.Role.organizer
        } else {
            try {
                repository.members(event.id).firstOrNull { it.userId == uid }?.role?.let {
                    resolved[event.id] = it
                }
            } catch (_: Throwable) {
                // Keep the card usable if one roster refresh fails. Never invent admin privileges.
            }
        }
        value = resolved.toMap()
    }
}

@Composable
private fun ShellEventCard(
    event: SnapEvent,
    role: EventMember.Role?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).background(shellGradient(), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(shellCategoryIcon(event.category), null, tint = Color.White, modifier = Modifier.size(31.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.name, fontSize = 19.sp, fontWeight = FontWeight.Black, color = ShellColors.Ink)
                ShellRoleLine(role)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = ShellColors.Secondary, modifier = Modifier.size(15.dp))
                    Text(
                        "  ${shellEventRange(event)}",
                        color = ShellColors.Secondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    shellEventStatus(event),
                    color = ShellColors.Coral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
                Icon(Icons.Filled.ChevronRight, "Open Event", tint = Color.Gray, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun ShellRoleLine(role: EventMember.Role?) {
    val label = shellRoleLabel(role)
    val icon = shellRoleIcon(role)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ShellColors.Secondary, modifier = Modifier.size(15.dp))
        Text("  $label", color = ShellColors.Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShellInvitationReview(event: SnapEvent, coordinator: AppCoordinator) {
    BackHandler { coordinator.dismissPendingInvite() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = coordinator::dismissPendingInvite) { Text("Cancel") }
            Text("Invitation", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.size(64.dp))
        }
        Spacer(Modifier.height(34.dp))
        ShellBrandMark(74)
        Text("You're invited to", color = ShellColors.Secondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        Text(event.name, fontSize = 31.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        ShellCard(Modifier.padding(top = 22.dp)) {
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, null, tint = ShellColors.Lilac)
                Text("  ${shellEventRange(event)}", fontWeight = FontWeight.Bold)
            }
            event.locationName?.let {
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = ShellColors.Coral)
                    Text("  $it")
                }
            }
        }
        Text(
            "Join this Event to get your photos found on other participating members' phones.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 22.dp),
        )
        ShellPrimaryButton("Join Event", Icons.Filled.CheckCircle, coordinator::confirmPendingInvite)
        TextButton(onClick = coordinator::dismissPendingInvite) { Text("Not now") }
    }
}

@Composable
private fun ShellEventHost(state: AppUiState, coordinator: AppCoordinator) {
    val event = state.selectedEvent ?: return
    var page by rememberSaveable(event.id) { mutableStateOf(ShellEventPage.DASHBOARD) }

    BackHandler {
        when (page) {
            ShellEventPage.DASHBOARD -> coordinator.closeEvent()
            ShellEventPage.PHONE_INVITE -> page = ShellEventPage.INVITE
            else -> page = ShellEventPage.DASHBOARD
        }
    }

    when (page) {
        ShellEventPage.DASHBOARD -> ShellEventDashboard(state, coordinator) { page = it }
        ShellEventPage.PHOTOS -> ShellEventPhotos(state, coordinator) { page = ShellEventPage.DASHBOARD }
        ShellEventPage.SCAN -> ShellEventScan(state, coordinator) { page = ShellEventPage.DASHBOARD }
        ShellEventPage.MEMBERS -> ShellMembers(state, coordinator, { page = ShellEventPage.DASHBOARD }) { page = ShellEventPage.INVITE }
        ShellEventPage.INVITE -> ShellInvite(
            state = state,
            onBack = { page = ShellEventPage.DASHBOARD },
            onPhoneInvite = { page = ShellEventPage.PHONE_INVITE },
        )
        ShellEventPage.PHONE_INVITE -> ParityPhoneInviteScreen(event = event) { page = ShellEventPage.INVITE }
        ShellEventPage.EDIT -> ShellEventFormDialog(
            title = "Edit Event",
            initialName = event.name,
            initialCategory = event.category,
            initialLocation = event.locationName.orEmpty(),
            initialStart = event.startsAt.atZone(shellEventZone(event)).toLocalDate(),
            initialEnd = event.endsAt.atZone(shellEventZone(event)).toLocalDate(),
            submitLabel = "Save Changes",
            onDismiss = { page = ShellEventPage.DASHBOARD },
            onSubmit = { name, category, location, start, end ->
                coordinator.editSelectedEvent(name, category, location, start, end)
                page = ShellEventPage.DASHBOARD
            },
        )
    }
}

@Composable
private fun ShellEventDashboard(
    state: AppUiState,
    coordinator: AppCoordinator,
    navigate: (ShellEventPage) -> Unit,
) {
    val event = state.selectedEvent ?: return
    val uid = state.user?.id
    val role = shellCurrentRole(event, state.members, uid)
    val me = state.members.firstOrNull { it.userId == uid }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = coordinator::closeEvent) {
                Icon(Icons.Filled.ChevronLeft, null)
                Text("Events")
            }
        }
        ShellEventHero(event, role)

        ShellCard(Modifier.padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CameraAlt,
                    null,
                    tint = if (me?.sharingEnabled == true) Color(0xFF008F83) else Color.Gray,
                    modifier = Modifier.size(30.dp),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Photo Scan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (me?.sharingEnabled == true) "SnapLoop is ready to check this Event for new photos" else "Photo sharing is turned off for this Event",
                        color = ShellColors.Secondary,
                        fontSize = 13.sp,
                    )
                }
                Text(if (me?.sharingEnabled == true) "Ready" else "Paused", fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShellActionCard(Icons.Filled.Image, "My Photos", "${state.photos.size} found of you", Modifier.weight(1f)) {
                navigate(ShellEventPage.PHOTOS)
            }
            ShellActionCard(Icons.Filled.PhotoLibrary, "Scan Photos", "Check New Event Photos", Modifier.weight(1f)) {
                navigate(ShellEventPage.SCAN)
            }
        }

        ShellCard(Modifier.padding(horizontal = 18.dp).clickable { navigate(ShellEventPage.MEMBERS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, tint = ShellColors.Lilac, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Event Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${state.members.size} member${if (state.members.size == 1) "" else "s"}", color = ShellColors.Secondary)
                }
                Icon(Icons.Filled.ChevronRight, "View members", tint = Color.Gray)
            }
        }

        if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
            ShellCard(Modifier.padding(horizontal = 18.dp).clickable { navigate(ShellEventPage.INVITE) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Share, null, tint = ShellColors.Coral, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Invite People", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Share code, link, QR or phone invite", color = ShellColors.Secondary, fontSize = 13.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
                }
            }
        }

        if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
            ShellCard(Modifier.padding(horizontal = 18.dp)) {
                Text(
                    if (role == EventMember.Role.admin) "Admin Controls" else "Organizer Controls",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                )
                if (event.status == EventStatus.active) {
                    TextButton(onClick = { navigate(ShellEventPage.EDIT) }) {
                        Icon(Icons.Filled.Edit, null)
                        Text("  Edit Event")
                    }
                    TextButton(onClick = coordinator::endSelectedEvent) { Text("End Event", color = Color.Red) }
                }
                if (role == EventMember.Role.organizer) {
                    if (event.status == EventStatus.endedByOrganizer) {
                        TextButton(onClick = coordinator::reopenSelectedEvent) { Text("Reopen Event") }
                    }
                    if (event.status == EventStatus.deletedByOrganizer) {
                        TextButton(onClick = coordinator::restoreSelectedEvent) { Text("Restore Event") }
                    } else {
                        TextButton(onClick = coordinator::moveSelectedEventToDeleted) {
                            Icon(Icons.Filled.Delete, null, tint = Color.Red)
                            Text("  Move to Deleted", color = Color.Red)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ShellEventHero(event: SnapEvent, role: EventMember.Role?) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(230.dp)
            .background(shellGradient(), RoundedCornerShape(30.dp)).padding(20.dp),
    ) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart),
            color = Color.White.copy(alpha = 0.22f),
            shape = RoundedCornerShape(50),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(shellRoleIcon(role), null, tint = Color.White, modifier = Modifier.size(17.dp))
                Text(
                    "  ${shellRoleLabel(role)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(shellEventStatus(event), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(event.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.CalendarMonth, null, tint = Color.White.copy(alpha = 0.94f), modifier = Modifier.size(18.dp))
                Text("  ${shellEventRange(event)}", color = Color.White.copy(alpha = 0.94f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShellEventPhotos(state: AppUiState, coordinator: AppCoordinator, onBack: () -> Unit) {
    ParityPhotoGallery(
        title = "My Photos",
        subtitle = if (state.photos.size == 1) "1 photo of you found in this Event" else "${state.photos.size} photos of you found in this Event",
        photos = state.photos,
        userId = state.user?.id,
        onRefresh = coordinator::refreshPhotos,
        onBack = onBack,
    )
}

@Composable
private fun ShellEventScan(state: AppUiState, coordinator: AppCoordinator, onBack: () -> Unit) {
    ParityEventScanScreen(state = state, coordinator = coordinator, onBack = onBack)
}

@Composable
private fun ShellMembers(
    state: AppUiState,
    coordinator: AppCoordinator,
    onBack: () -> Unit,
    onInvite: () -> Unit,
) {
    val uid = state.user?.id
    val event = state.selectedEvent ?: return
    val me = state.members.firstOrNull { it.userId == uid }
    val role = shellCurrentRole(event, state.members, uid)
    var leaveConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShellSubpageHeader("Members", onBack)
        ShellCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(shellRoleIcon(role), null, tint = ShellColors.Coral)
                Text("  You are ${shellRoleLabel(role).lowercase().replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Black)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Share matched pictures from my phone in this Event", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = me?.sharingEnabled == true, onCheckedChange = coordinator::setSharing, enabled = me != null)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show my own matched pictures from this phone in my Gallery", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(
                    checked = state.includeOwnMatches,
                    onCheckedChange = coordinator::setIncludeOwnMatches,
                    enabled = me?.sharingEnabled == true && state.user?.hasFaceProfile == true,
                )
            }
            if (role == EventMember.Role.organizer || role == EventMember.Role.admin) {
                ShellPrimaryButton("Invite People", Icons.Filled.Share, onInvite)
            }
            if (role != EventMember.Role.organizer) {
                TextButton(onClick = { leaveConfirm = true }) { Text("Leave Event", color = Color.Red) }
            }
        }

        Text("Event Members", fontSize = 21.sp, fontWeight = FontWeight.Black)
        ShellCard {
            state.members.forEachIndexed { index, member ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(shellGradient(), CircleShape), contentAlignment = Alignment.Center) {
                        Text((member.displayName ?: "•").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text((member.displayName ?: "Event member") + if (member.userId == uid) " (You)" else "", fontWeight = FontWeight.Bold)
                        ShellRoleLine(member.role)
                    }
                }
                if (index != state.members.lastIndex) HorizontalDivider()
            }
        }
    }

    if (leaveConfirm) {
        AlertDialog(
            onDismissRequest = { leaveConfirm = false },
            title = { Text("Leave this Event?") },
            text = { Text("Your membership will be removed from this Event.") },
            confirmButton = {
                TextButton(onClick = { leaveConfirm = false; coordinator.leaveSelectedEvent() }) { Text("Leave", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { leaveConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShellInvite(
    state: AppUiState,
    onBack: () -> Unit,
    onPhoneInvite: () -> Unit,
) {
    val event = state.selectedEvent ?: return
    val context = LocalContext.current
    val inviteUrl = DeepLinkParser.inviteUrl(event.inviteToken)
    val shareText = DeepLinkParser.shareText(event.name, state.user?.displayName, event.inviteToken)
    var copied by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShellSubpageHeader("Invite", onBack)
        ShellBrandMark(64)
        Text("Invite people to ${event.name}", fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text("Anyone with the invite can open the Event, sign in, and choose whether to join.", textAlign = TextAlign.Center, color = ShellColors.Secondary)
        ShellPrimaryButton("Share Invite", Icons.Filled.Share, onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Share SnapLoop Invite"))
        })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { shellCopy(context, DeepLinkParser.formatCode(event.joinCode)); copied = "Code copied" },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, null)
                Text("  Copy Code")
            }
            OutlinedButton(
                onClick = { shellCopy(context, inviteUrl); copied = "Link copied" },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, null)
                Text("  Copy Link")
            }
        }
        copied?.let { Text("✓ $it", color = Color(0xFF008F61), fontWeight = FontWeight.Bold) }

        ShellCard(Modifier.clickable(onClick = onPhoneInvite)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(shellSoftGradient(), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = ShellColors.Lilac)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Invite by Phone or Contacts", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        "Existing users get an in-app invite; others can receive the link.",
                        color = ShellColors.Secondary,
                        fontSize = 12.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
            }
        }

        ShellCard {
            Text("Scan to join", fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
            QrCodeImage(
                inviteUrl,
                Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(220.dp).clip(RoundedCornerShape(18.dp)),
            )
            Text(
                DeepLinkParser.formatCode(event.joinCode),
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
            )
            Text("Event code", color = ShellColors.Secondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ShellGallery(state: AppUiState, coordinator: AppCoordinator) {
    ParityPhotoGallery(
        title = "Gallery",
        subtitle = if (state.allPhotos.size == 1) "1 photo of you across all Events" else "${state.allPhotos.size} photos of you across all Events",
        photos = state.allPhotos,
        userId = state.user?.id,
        onRefresh = coordinator::refreshAllPhotos,
    )
}

@Composable
private fun ShellMatchCard(match: PhotoMatch, modifier: Modifier = Modifier) {
    ShellCard(modifier) {
        MatchedThumbnailCell(
            path = match.thumbnailPath,
            modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(18.dp)),
            maxPixelSize = 900,
        )
        Text("Matched photo", fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        Text(shellFormatMillis(match.capturedAtMillis), color = ShellColors.Secondary, fontSize = 13.sp)
    }
}

@Composable
private fun ShellYou(state: AppUiState, coordinator: AppCoordinator) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var signOutConfirm by remember { mutableStateOf(false) }
    var replayConfirm by remember { mutableStateOf(false) }
    var photoPermissionGranted by remember { mutableStateOf(shellHasPhotoPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photoPermissionGranted = shellHasPhotoPermission(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("You", fontSize = 34.sp, fontWeight = FontWeight.Black)
        ShellCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaceReferenceThumbnail(
                    userId = state.user?.id,
                    fallbackInitial = state.user?.displayName ?: "?",
                    modifier = Modifier.size(66.dp),
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(state.user?.displayName ?: "Add your name", fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(state.user?.phoneNumber.orEmpty(), color = ShellColors.Secondary, fontSize = 13.sp)
                    Text(
                        if (state.user?.hasFaceProfile == true) "✓ Face Setup Active" else "Face Setup not completed",
                        color = if (state.user?.hasFaceProfile == true) Color(0xFF008F61) else ShellColors.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ShellBrandMark(34)
            }
        }

        ShellSettingsCard(Icons.Filled.Edit, if (state.user?.displayName.isNullOrBlank()) "Add Your Name" else "Edit Your Name", "Name shown to people in your Events") {
            editName = true
        }
        ShellSettingsCard(Icons.Filled.Person, if (state.user?.hasFaceProfile == true) "Update Face Setup" else "Set Up Your Face", "Guided face scan and Face Setup controls") {
            coordinator.openFaceSetupFromMain()
        }
        ShellCard {
            Text("Photo Access", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(if (photoPermissionGranted) "Photos access is enabled" else "Photo access is off or not yet granted", color = ShellColors.Secondary, fontSize = 13.sp)
            if (!photoPermissionGranted) {
                ShellPrimaryButton("Allow Photo Access", Icons.Filled.PhotoLibrary, { permissionLauncher.launch(shellPhotoPermissions()) })
            } else {
                OutlinedButton(onClick = { shellOpenAppSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Manage Photo Access") }
            }
        }
        ShellSettingsCard(Icons.Filled.PrivacyTip, "Privacy & Data", "Face data, deletion and account controls") { privacy = true }
        ShellSettingsCard(Icons.Filled.RestartAlt, "Replay Onboarding", "Review how Events, matching and permissions work") { replayConfirm = true }
        ShellSettingsCard(Icons.Filled.Logout, "Sign Out", "Sign out without deleting your account") { signOutConfirm = true }
        Spacer(Modifier.height(16.dp))
    }

    if (editName) {
        var name by remember(state.user?.displayName) { mutableStateOf(state.user?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Edit Your Name") },
            text = { OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { editName = false; coordinator.saveDisplayName(name) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }
    if (privacy) {
        ParityPrivacyScreen(
            onDismiss = { privacy = false },
            onWithdraw = { privacy = false; coordinator.withdrawBiometrics() },
            onDelete = { privacy = false; coordinator.deleteAccount() },
        )
    }
    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text("Sign out of SnapLoop?") },
            confirmButton = { TextButton(onClick = { signOutConfirm = false; coordinator.signOut() }) { Text("Sign Out", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text("Cancel") } },
        )
    }
    if (replayConfirm) {
        AlertDialog(
            onDismissRequest = { replayConfirm = false },
            title = { Text("Replay onboarding?") },
            text = { Text("Your account, Events, photos and Face Setup will not be changed.") },
            confirmButton = { TextButton(onClick = { replayConfirm = false; coordinator.replayOnboarding() }) { Text("Replay Onboarding") } },
            dismissButton = { TextButton(onClick = { replayConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShellEventFormDialog(
    title: String,
    initialName: String,
    initialCategory: EventCategory,
    initialLocation: String,
    initialStart: LocalDate,
    initialEnd: LocalDate,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String, EventCategory, String?, LocalDate, LocalDate) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var categoryMenu by remember { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(initialLocation) }
    var startsOn by remember { mutableStateOf(initialStart) }
    var endsOn by remember { mutableStateOf(initialEnd) }
    val today = LocalDate.now()
    val lower = today.minusDays(15)
    val upper = today.plusDays(15)
    val invalid = endsOn.isBefore(startsOn) || startsOn.isBefore(lower) || startsOn.isAfter(upper) ||
        endsOn.isBefore(lower) || endsOn.isAfter(upper) || ChronoUnit.DAYS.between(startsOn, endsOn) > 15

    ShellFullDialog(onDismiss) {
        ShellBackground {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShellSubpageHeader(if (title.startsWith("Create")) "New Event" else "Edit Event", onDismiss)
                ShellBrandMark(58)
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
                ShellCard(Modifier.padding(top = 18.dp)) {
                    Text("Event name", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        name,
                        { name = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Banff Weekend") },
                        singleLine = true,
                        supportingText = { Text("${name.length}/20") },
                    )
                    Text("Type", fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(shellCategoryName(category)) }
                        DropdownMenu(categoryMenu, { categoryMenu = false }) {
                            EventCategory.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(shellCategoryName(item)) },
                                    onClick = { category = item; categoryMenu = false },
                                )
                            }
                        }
                    }
                    Text("Location", fontWeight = FontWeight.Bold)
                    OutlinedTextField(location, { location = it.take(80) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Optional") }, singleLine = true)
                }
                ShellCard(Modifier.padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = ShellColors.Coral)
                        Text("  Event dates", fontWeight = FontWeight.Black)
                    }
                    ShellDateField("Starts", startsOn, lower, upper) { newStart ->
                        startsOn = newStart
                        if (endsOn.isBefore(newStart)) endsOn = newStart
                        if (ChronoUnit.DAYS.between(newStart, endsOn) > 15) endsOn = minOf(upper, newStart.plusDays(3))
                    }
                    ShellDateField("Ends", endsOn, startsOn, minOf(upper, startsOn.plusDays(15))) { endsOn = it }
                    Text(
                        "SnapLoop only considers photos taken within this Event's selected date range. Dates must stay within 15 days before or after today, and an Event can span at most 15 calendar days.",
                        color = ShellColors.Secondary,
                        fontSize = 12.sp,
                    )
                    if (invalid) Text("Choose a valid Event date range.", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                ShellPrimaryButton(
                    submitLabel,
                    Icons.Filled.CheckCircle,
                    onClick = { onSubmit(name.trim(), category, location.trim().takeIf(String::isNotEmpty), startsOn, endsOn) },
                    modifier = Modifier.padding(top = 18.dp),
                    enabled = name.trim().isNotEmpty() && !invalid,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ShellDateField(label: String, value: LocalDate, minimum: LocalDate, maximum: LocalDate, onValue: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val picked = LocalDate.of(year, month + 1, day)
                    if (!picked.isBefore(minimum) && !picked.isAfter(maximum)) onValue(picked)
                },
                value.year,
                value.monthValue - 1,
                value.dayOfMonth,
            ).apply {
                datePicker.minDate = minimum.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                datePicker.maxDate = maximum.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            }.show()
        }) { Text(value.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) }
    }
}

@Composable
private fun ShellJoinDialog(onDismiss: () -> Unit, onResolve: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var scanQr by rememberSaveable { mutableStateOf(false) }
    if (scanQr) {
        ShellFullDialog({ scanQr = false }) {
            QrCodeScannerScreen(
                onResult = { value -> scanQr = false; onResolve(value) },
                onCancel = { scanQr = false },
            )
        }
        return
    }
    ShellFullDialog(onDismiss) {
        ShellBackground {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShellSubpageHeader("Join Event", onDismiss)
                Spacer(Modifier.height(44.dp))
                ShellBrandMark(62)
                Text("Join an Event", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
                Text("Enter an Event code or invite link, or scan the Event QR code.", textAlign = TextAlign.Center, color = ShellColors.Secondary, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    text,
                    { text = it.take(512) },
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    placeholder = { Text("Event code or invite link") },
                    singleLine = true,
                )
                ShellPrimaryButton("Continue", Icons.Filled.ChevronRight, { onResolve(text) }, Modifier.padding(top = 14.dp), text.trim().isNotEmpty())
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f)); Text("  or  ", color = Color.Gray); HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(onClick = { scanQr = true }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Filled.QrCodeScanner, null, tint = ShellColors.Lilac)
                    Text("  Scan QR Code", color = ShellColors.Lilac, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShellSettingsCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ShellCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(shellSoftGradient(), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ShellColors.Lilac)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = ShellColors.Secondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
        }
    }
}

@Composable
private fun ShellPrivacyDialog(onDismiss: () -> Unit, onWithdraw: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete SnapLoop account?") },
            text = { Text("This requests permanent deletion of your SnapLoop account and server-side data. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete Permanently", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Face matching is explicit-consent only and scanning is Event-scoped.")
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.fillMaxWidth()) { Text("Withdraw Biometric Consent") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Account", color = Color.Red) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ShellSubpageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, "Back") }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun ShellActionCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxWidth().height(154.dp).background(shellGradient()).padding(16.dp)) {
            Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ShellPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(if (enabled) shellGradient() else Brush.linearGradient(listOf(Color.LightGray, Color.Gray))),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White)
                Text("  $text", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ShellCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun ShellInsightBanner(value: String, label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(shellGradient(), RoundedCornerShape(24.dp)).padding(18.dp)) {
        Column {
            Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShellSectionTitle(title: String) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = ShellColors.Ink, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun ShellBrandMark(size: Int) {
    Box(
        Modifier.size(size.dp).background(shellGradient(), RoundedCornerShape((size * 0.22f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("S", color = Color.White, fontSize = (size * 0.52f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ShellBackground(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color.White, Color(0xFFFFF8FB), Color(0xFFFBF7FF), Color.White),
            ),
        ),
        content = content,
    )
}

@Composable
private fun ShellFullDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) { content() }
    }
}

private object ShellColors {
    val Ink = Color(0xFF201C24)
    val Secondary = Color(0xFF6B6570)
    val Coral = Color(0xFFFF4F67)
    val HotPink = Color(0xFFFF2B90)
    val Lilac = Color(0xFF8E63F6)
    val Blue = Color(0xFF345CFF)
    val Orange = Color(0xFFFF7438)
}

private fun shellGradient() = Brush.linearGradient(
    listOf(ShellColors.Orange, ShellColors.Coral, ShellColors.HotPink, ShellColors.Lilac, ShellColors.Blue),
)
private fun shellSoftGradient() = Brush.linearGradient(listOf(Color(0xFFFFE4DC), Color(0xFFF4DEFF), Color(0xFFDDE6FF)))

private fun shellCurrentRole(event: SnapEvent, members: List<EventMember>, uid: String?): EventMember.Role? {
    if (uid == null) return null
    if (event.creatorUserId == uid) return EventMember.Role.organizer
    return members.firstOrNull { it.userId == uid }?.role
}

private fun shellRoleLabel(role: EventMember.Role?): String = when (role) {
    EventMember.Role.organizer -> "ORGANIZER"
    EventMember.Role.admin -> "ADMIN"
    EventMember.Role.participant -> "MEMBER"
    null -> "MEMBER"
}

private fun shellRoleIcon(role: EventMember.Role?): ImageVector = when (role) {
    EventMember.Role.organizer -> Icons.Filled.WorkspacePremium
    EventMember.Role.admin -> Icons.Filled.Shield
    EventMember.Role.participant, null -> Icons.Filled.Person
}

private fun shellCategoryName(category: EventCategory): String = when (category) {
    EventCategory.trip -> "Trip"
    EventCategory.wedding -> "Wedding"
    EventCategory.party -> "Party"
    EventCategory.birthday -> "Birthday"
    EventCategory.conference -> "Conference"
    EventCategory.family -> "Family"
    EventCategory.sports -> "Sports"
    EventCategory.other -> "Other"
}

private fun shellCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.LocationOn
    EventCategory.wedding -> Icons.Filled.Image
    EventCategory.party -> Icons.Filled.Groups
    EventCategory.birthday -> Icons.Filled.MoreHoriz
    EventCategory.conference -> Icons.Filled.Groups
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.MoreHoriz
    EventCategory.other -> Icons.Filled.PhotoLibrary
}

private fun shellEventStatus(event: SnapEvent): String {
    if (event.status == EventStatus.endedByOrganizer) return "ENDED"
    if (event.status == EventStatus.deletedByOrganizer) return "DELETED"
    if (event.status == EventStatus.expired) return "COMPLETED"
    val now = Instant.now()
    return when {
        now.isBefore(event.startsAt) -> "UPCOMING"
        !now.isAfter(event.endsAt) -> "LIVE"
        else -> "PHOTO WINDOW"
    }
}

private fun shellEventZone(event: SnapEvent): ZoneId = runCatching {
    ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
}.getOrDefault(ZoneId.systemDefault())

private fun shellEventRange(event: SnapEvent): String {
    val zone = shellEventZone(event)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val start = event.startsAt.atZone(zone).toLocalDate().format(formatter)
    val end = event.endsAt.atZone(zone).toLocalDate().format(formatter)
    return if (start == end) start else "$start – $end"
}

private fun shellFormatMillis(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

private fun shellCopy(context: Context, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("SnapLoop", value))
}

private fun shellPhotoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun shellHasPhotoPermission(context: Context): Boolean = shellPhotoPermissions().any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun shellOpenAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
