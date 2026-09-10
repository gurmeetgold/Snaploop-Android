package com.snaploop.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var copiedAction by remember { mutableStateOf<ShareEventParitySpec.CopyAction?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedAction) {
        if (copiedAction != null) {
            delay(ShareEventParitySpec.COPY_FEEDBACK_DURATION_MS)
            copiedAction = null
            feedback = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        ParityBrandBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ShareEventParitySpec.CONTENT_SPACING_DP.dp),
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

                ParityBrandMark(ShareEventParitySpec.BRAND_MARK_DP)
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
                    leadingContent = {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White)
                    },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShareSecondaryAction(
                        action = ShareEventParitySpec.CopyAction.CODE,
                        confirmed = copiedAction == ShareEventParitySpec.CopyAction.CODE,
                        defaultIcon = Icons.Filled.ContentCopy,
                        modifier = Modifier.weight(1f),
                    ) {
                        copyToClipboard(context, "SnapLoop Event code", formattedCode)
                        copiedAction = ShareEventParitySpec.CopyAction.CODE
                        feedback = "Event code copied"
                    }
                    ShareSecondaryAction(
                        action = ShareEventParitySpec.CopyAction.LINK,
                        confirmed = copiedAction == ShareEventParitySpec.CopyAction.LINK,
                        defaultIcon = Icons.Filled.Link,
                        modifier = Modifier.weight(1f),
                    ) {
                        copyToClipboard(context, "SnapLoop invite link", inviteUrl)
                        copiedAction = ShareEventParitySpec.CopyAction.LINK
                        feedback = "Invite link copied"
                    }
                }

                if (canManageInvites) {
                    ParityPremiumCard(
                        modifier = Modifier.clickable(onClick = onPhoneInvite),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(ShareEventParitySpec.PHONE_ICON_WELL_DP.dp)
                                    .background(
                                        SnapColors.Mint.copy(alpha = 0.14f),
                                        RoundedCornerShape(12.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = SnapColors.Mint,
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    ShareEventParitySpec.PHONE_INVITE_TITLE,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                )
                                Text(
                                    ShareEventParitySpec.PHONE_INVITE_SUBTITLE,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                    fontSize = 12.sp,
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                            )
                        }
                    }
                }

                ParityPremiumCard {
                    Text("Scan to join", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(ShareEventParitySpec.QR_SIZE_DP.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                    ) {
                        QrCodeImage(
                            value = inviteUrl,
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                        )
                    }
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

                Spacer(Modifier.height(24.dp))
            }
        }

        feedback?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2EAD63),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ShareSecondaryAction(
    action: ShareEventParitySpec.CopyAction,
    confirmed: Boolean,
    defaultIcon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (confirmed) Color(0xFF2EAD63) else SnapColors.Coral
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(ShareEventParitySpec.SECONDARY_ACTION_HEIGHT_DP.dp),
        shape = RoundedCornerShape(ShareEventParitySpec.SECONDARY_ACTION_RADIUS_DP.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = tint,
            containerColor = if (confirmed) tint.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Icon(
            if (confirmed) Icons.Filled.CheckCircle else defaultIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "  ${ShareEventParitySpec.actionTitle(action, confirmed)}",
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
}
