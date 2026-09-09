package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snaploop.app.notifications.EventNotification
import com.snaploop.app.notifications.EventNotificationClient
import com.snaploop.app.notifications.EventNotificationInvalidationStore
import com.snaploop.app.notifications.EventNotificationPolicy
import com.snaploop.app.notifications.SnapLoopPushRefreshStore
import kotlinx.coroutines.launch

/** Home Updates surface mirrored from pinned iOS HomeView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParityHomeUpdates(userId: String?) {
    val uid = userId?.takeIf { it.isNotBlank() } ?: return
    val client = remember(uid) { EventNotificationClient() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val pushRefreshPending by SnapLoopPushRefreshStore.pending.collectAsState()
    var notifications by remember(uid) { mutableStateOf<List<EventNotification>>(emptyList()) }
    var showAll by remember(uid) { mutableStateOf(false) }

    suspend fun reload() {
        val refreshed = runCatching { client.unread(uid) }.getOrNull() ?: return
        notifications = refreshed
    }

    LaunchedEffect(uid) { reload() }

    LaunchedEffect(pushRefreshPending) {
        if (pushRefreshPending) reload()
    }

    LaunchedEffect(uid) {
        EventNotificationInvalidationStore.eventIds.collect { eventId ->
            notifications = EventNotificationPolicy.withoutEvent(notifications, eventId)
        }
    }

    LaunchedEffect(notifications.isEmpty(), showAll) {
        if (notifications.isEmpty() && showAll) showAll = false
    }

    DisposableEffect(lifecycleOwner, uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reload() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (notifications.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Updates",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (notifications.size > 1) {
                TextButton(onClick = { showAll = true }) {
                    Text("View all", color = SnapColors.Lilac, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.padding(start = 5.dp),
                        shape = CircleShape,
                        color = SnapColors.Lilac.copy(alpha = 0.12f),
                    ) {
                        Text(
                            notifications.size.toString(),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = SnapColors.Lilac,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        EventNotificationCard(
            notification = notifications.first(),
            modifier = Modifier.padding(horizontal = 18.dp),
            onDismiss = { notification ->
                notifications = notifications.filterNot { it.id == notification.id }
                scope.launch { runCatching { client.markRead(uid, notification.id) } }
            },
        )
    }

    if (showAll) {
        ModalBottomSheet(onDismissRequest = { showAll = false }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Updates",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                TextButton(onClick = { showAll = false }) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                notifications.forEach { notification ->
                    EventNotificationCard(
                        notification = notification,
                        onDismiss = { dismissed ->
                            notifications = notifications.filterNot { it.id == dismissed.id }
                            scope.launch { runCatching { client.markRead(uid, dismissed.id) } }
                        },
                    )
                }
                Spacer(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EventNotificationCard(
    notification: EventNotification,
    modifier: Modifier = Modifier,
    onDismiss: (EventNotification) -> Unit,
) {
    ParityPremiumCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(SnapColors.Mint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = SnapColors.Mint,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    notification.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    notification.body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = { onDismiss(notification) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss update",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}
