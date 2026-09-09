package com.snaploop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Authenticated You screen mirrored from pinned iOS SettingsView.swift. */
@Composable
internal fun ParityYouScreen(
    state: AppUiState,
    coordinator: AppCoordinator,
) {
    var editName by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var signOutConfirm by remember { mutableStateOf(false) }
    var replayConfirm by remember { mutableStateOf(false) }

    if (editName) {
        ParityNameSetupScreen(
            initialName = state.user?.displayName.orEmpty(),
            onSave = { value ->
                editName = false
                coordinator.saveDisplayName(value)
            },
            onBack = { editName = false },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = YouScreenParitySpec.HORIZONTAL_PADDING_DP.dp,
                vertical = YouScreenParitySpec.VERTICAL_PADDING_DP.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(YouScreenParitySpec.SECTION_SPACING_DP.dp),
    ) {
        Text(
            "You",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        ParityPremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaceReferenceThumbnail(
                    userId = state.user?.id,
                    fallbackInitial = state.user?.displayName ?: "?",
                    modifier = Modifier.size(YouScreenParitySpec.PROFILE_THUMBNAIL_DP.dp),
                )
                Column(
                    Modifier.weight(1f).padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        state.user?.displayName ?: "Add your name",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.user?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                        Text(
                            phone,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                        )
                    }
                    if (state.user?.hasFaceProfile == true) {
                        Text(
                            "✓ Face Setup Active",
                            color = Color(0xFF1B8F55),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                ParityBrandMark(YouScreenParitySpec.PROFILE_MARK_DP)
            }
        }

        ParityPremiumCard {
            YouAccountRow(
                title = if (state.user?.displayName == null) "Add Your Name" else "Edit Your Name",
                icon = Icons.Filled.Edit,
                tint = SnapColors.Coral,
                onClick = { editName = true },
            )
            HorizontalDivider(Modifier.padding(start = YouScreenParitySpec.ACCOUNT_DIVIDER_START_DP.dp))
            YouAccountRow(
                title = if (state.user?.hasFaceProfile == true) "Update Face Setup" else "Set Up Your Face",
                icon = Icons.Filled.Face,
                tint = SnapColors.Lilac,
                onClick = coordinator::openFaceSetupFromMain,
            )
        }

        ParityPhotoAccessCard()

        ParityPremiumCard(
            modifier = Modifier.clickable { privacy = true },
        ) {
            YouMenuRow(
                title = "Privacy & Data",
                subtitle = "Face data, deletion and account controls",
                icon = Icons.Filled.PrivacyTip,
                tint = SnapColors.Blue,
                showChevron = true,
            )
        }

        ParityPremiumCard(
            modifier = Modifier.clickable { replayConfirm = true },
        ) {
            YouMenuRow(
                title = "Replay Onboarding",
                subtitle = "Review how Events, matching and permissions work",
                icon = Icons.Filled.RestartAlt,
                tint = SnapColors.Lilac,
                showChevron = true,
            )
        }

        ParityPremiumCard(
            modifier = Modifier.clickable { signOutConfirm = true },
        ) {
            YouMenuRow(
                title = "Sign Out",
                subtitle = null,
                icon = Icons.Filled.Logout,
                tint = MaterialTheme.colorScheme.error,
                titleColor = MaterialTheme.colorScheme.error,
                showChevron = false,
            )
        }

        Spacer(Modifier.size(2.dp))
    }

    if (privacy) {
        ParityPrivacyScreen(
            onDismiss = { privacy = false },
            onWithdraw = {
                privacy = false
                coordinator.withdrawBiometrics()
            },
            onDelete = {
                privacy = false
                coordinator.deleteAccount()
            },
        )
    }

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text("Sign out of SnapLoop?") },
            confirmButton = {
                TextButton(onClick = {
                    signOutConfirm = false
                    coordinator.signOut()
                }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { signOutConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (replayConfirm) {
        AlertDialog(
            onDismissRequest = { replayConfirm = false },
            title = { Text("Replay onboarding?") },
            text = {
                Text("You'll see the SnapLoop introduction again. Your account, Events, photos, and Face Setup will not be changed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    replayConfirm = false
                    coordinator.replayOnboarding()
                }) { Text("Replay Onboarding") }
            },
            dismissButton = {
                TextButton(onClick = { replayConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun YouAccountRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = YouScreenParitySpec.ACCOUNT_ROW_VERTICAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YouIconBadge(icon, tint)
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun YouMenuRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YouIconBadge(icon, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            subtitle?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun YouIconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(YouScreenParitySpec.ICON_BADGE_DP.dp)
            .background(
                tint.copy(alpha = 0.13f),
                RoundedCornerShape(YouScreenParitySpec.ICON_BADGE_RADIUS_DP.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}
