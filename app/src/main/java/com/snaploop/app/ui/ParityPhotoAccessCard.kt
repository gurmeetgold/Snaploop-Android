package com.snaploop.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** iOS SettingsView photo-access card translated to Android permission semantics. */
@Composable
internal fun ParityPhotoAccessCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(PhotoAccessParity.state(context)) }

    fun refresh() {
        access = PhotoAccessParity.state(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestAccess() {
        PhotoAccessParity.markRequested(context)
        permissionLauncher.launch(PhotoAccessParity.requestPermissions())
    }

    ParityPremiumCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconBadge(
                icon = Icons.Filled.PhotoLibrary,
                tint = SnapColors.Mint,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Photo Access",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    PhotoAccessParity.description(access),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                )
            }
        }

        when (access) {
            PhotoAccessState.NOT_REQUESTED -> PhotoAccessGradientButton(
                text = "Allow Photo Access",
                icon = Icons.Filled.PhotoLibrary,
                onClick = ::requestAccess,
            )

            PhotoAccessState.LIMITED -> PhotoAccessGradientButton(
                text = "Add More Photos",
                icon = Icons.Filled.PhotoLibrary,
                onClick = ::requestAccess,
            )

            PhotoAccessState.AUTHORIZED -> PhotoAccessSettingsButton(
                text = "Manage Photo Access",
                onClick = { openAppSettings(context) },
            )

            PhotoAccessState.DENIED -> PhotoAccessSettingsButton(
                text = "Open Android Settings",
                onClick = { openAppSettings(context) },
            )
        }
    }
}

@Composable
private fun SettingsIconBadge(icon: ImageVector, tint: Color) {
    val shape = RoundedCornerShape(BrandVisualParitySpec.SETTINGS_ICON_BADGE_RADIUS_DP.dp)
    Box(
        modifier = Modifier
            .size(BrandVisualParitySpec.SETTINGS_ICON_BADGE_DP.dp)
            .background(tint.copy(alpha = 0.13f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PhotoAccessGradientButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    PhotoAccessButton(
        text = text,
        icon = icon,
        foreground = Color.White,
        background = SnapGradients.Social,
        onClick = onClick,
    )
}

@Composable
private fun PhotoAccessSettingsButton(
    text: String,
    onClick: () -> Unit,
) {
    PhotoAccessButton(
        text = text,
        icon = Icons.Filled.SettingsIcon,
        foreground = SnapColors.Coral,
        background = Brush.linearGradient(
            listOf(
                SnapColors.Peach.copy(alpha = 0.22f),
                SnapColors.Peach.copy(alpha = 0.22f),
            ),
        ),
        onClick = onClick,
    )
}

@Composable
private fun PhotoAccessButton(
    text: String,
    icon: ImageVector,
    foreground: Color,
    background: Brush,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(BrandVisualParitySpec.SETTINGS_PHOTO_ACTION_RADIUS_DP.dp)
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(BrandVisualParitySpec.SETTINGS_PHOTO_ACTION_HEIGHT_DP.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background, shape),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Text(text, color = foreground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}
