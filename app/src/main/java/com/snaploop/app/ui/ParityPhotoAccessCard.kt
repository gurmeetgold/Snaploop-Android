package com.snaploop.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        Text("Photo Access", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            PhotoAccessParity.description(access),
            color = SnapColors.Secondary,
            fontSize = 13.sp,
        )

        when (access) {
            PhotoAccessState.NOT_REQUESTED -> ParityPrimaryButton(
                text = "Allow Photo Access",
                onClick = ::requestAccess,
            )

            PhotoAccessState.LIMITED -> ParityPrimaryButton(
                text = "Add More Photos",
                onClick = ::requestAccess,
            )

            PhotoAccessState.AUTHORIZED -> OutlinedButton(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage Photo Access", color = SnapColors.Coral, fontWeight = FontWeight.Bold)
            }

            PhotoAccessState.DENIED -> OutlinedButton(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Android Settings", color = SnapColors.Coral, fontWeight = FontWeight.Bold)
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
