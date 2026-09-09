package com.snaploop.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.model.SnapEvent
import kotlinx.coroutines.delay

/** Full-screen Share Event surface mirrored from pinned iOS ShareEventView.swift. */
@Composable
internal fun ParityShareEventDialog(
    event: SnapEvent,
    inviterName: String?,
    canManageInvites: Boolean,
    onDismiss: () -> Unit,
) {
    var phoneInviteOpen by remember(event.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (phoneInviteOpen) {
                ParityPhoneInviteScreen(
                    event = event,
                    onBack = { phoneInviteOpen = false },
                )
            } else {
                ParityShareEventContent(
                    event = event,
                    inviterName = inviterName,
                    canManageInvites = canManageInvites,
                    onBack = onDismiss,
                    onPhoneInvite = { phoneInviteOpen = true },
                )
            }
        }
    }
}

@Composable
private fun ParityShareEventContent(
    event: SnapEvent,
    inviterName: String?,
    canManageInvites: Boolean,
    onBack: () -> Unit,
    onPhoneInvite: () -> Unit,
) {
    val context = LocalContext.current
    val inviteUrl = remember(event.inviteToken) { DeepLinkParser.inviteUrl(event.inviteToken) }
    val shareCopy = remember(event.name, inviterName, event.inviteToken) {
        DeepLinkParser.shareText(event.name, inviterName, event.inviteToken)
    }
    val formattedCode = remember(event.joinCode) { DeepLinkParser.formatCode(event.joinCode) }
    val cleanInviterName = inviterName?.trim()?.takeIf { it.isNotEmpty() }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(1_500L)
            feedback = null
        }
    }

    ParityBrandBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                }
                Text(
                    "Invite",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.size(48.dp))
            }

            ParityBrandMark(62)
            Text(
                "Invite people to ${event.name}",
                textAlign = TextAlign.Center,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (cleanInviterName != null) {
                    "Your invite will show that it was sent by $cleanInviterName. Anyone with the invite can open the Event, sign in, and choose whether to join."
                } else {
                    "Anyone with the invite can open the Event, sign in, and choose whether to join."
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            ParityPrimaryButton(
                text = "Share Invite",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareCopy)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share SnapLoop invite"))
                },
            )

            ParityPremiumCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "SnapLoop Event code", event.joinCode)
                            feedback = "Event code copied"
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Text("  Copy Code")
                    }
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "SnapLoop invite link", inviteUrl)
                            feedback = "Invite link copied"
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null)
                        Text("  Copy Link")
                    }
                }
                feedback?.let {
                    Text(
                        it,
                        color = SnapColors.Coral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (canManageInvites) {
                ParityPremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = SnapColors.Lilac)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Invite by Phone or Contacts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Existing users get an in-app invite; others can receive the link.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onPhoneInvite,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Text("  Phone / Contacts")
                    }
                }
            }

            ParityPremiumCard {
                Text("Scan to join", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                QrCodeImage(
                    value = inviteUrl,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(220.dp),
                )
                Text(
                    formattedCode,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Event code",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                )
            }

            OutlinedButton(
                onClick = {
                    copyToClipboard(context, "SnapLoop invite", shareCopy)
                    feedback = "Invite copied"
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("  Copy Full Invite")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
}
