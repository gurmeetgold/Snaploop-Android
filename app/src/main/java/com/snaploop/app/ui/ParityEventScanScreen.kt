package com.snaploop.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snaploop.app.media.PhotoAccessLevel
import com.snaploop.app.media.PhotoAccessState
import com.snaploop.app.scanner.ScanCancellationRegistry

@Composable
internal fun ParityEventScanScreen(
    state: AppUiState,
    coordinator: AppCoordinator,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var photoAccess by remember { mutableStateOf(PhotoAccessState.current(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        PhotoAccessState.markRequested(context)
        photoAccess = PhotoAccessState.current(context)
        if (photoAccess.canRead) coordinator.scanSelectedEvent()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                photoAccess = PhotoAccessState.current(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startScan() {
        photoAccess = PhotoAccessState.current(context)
        if (photoAccess.canRead) {
            coordinator.scanSelectedEvent()
        } else {
            PhotoAccessState.markRequested(context)
            permissionLauncher.launch(PhotoAccessState.requestPermissions())
        }
    }

    fun openPhotoSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(
                "Scan Photos",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.size(56.dp))
        }
        Spacer(Modifier.height(34.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    state.scanProgress != null -> {
                        val progress = state.scanProgress
                        Box(
                            Modifier.size(92.dp).background(
                                Brush.linearGradient(listOf(Color(0xFFF05C68), Color(0xFF8E63F6))),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        Text(
                            if (progress.total > 0) {
                                "Scanning ${progress.checked} of ${progress.total} Event photos"
                            } else {
                                "Preparing your Event photos…"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                        if (progress.total > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (progress.checked.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (photoAccess == PhotoAccessLevel.SELECTED) {
                            LimitedAccessNotice()
                        }
                        Text(
                            "Keep SnapLoop open in the foreground until the scan finishes.",
                            color = Color(0xFF6B6670),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = {
                                state.selectedEvent?.id?.let(ScanCancellationRegistry::cancel)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Stop Scan", fontWeight = FontWeight.Bold)
                        }
                    }

                    state.scanResult != null -> {
                        val result = state.scanResult
                        val presentation = ScanResultPresentationPolicy.kind(result.remaining, result.failed)

                        if (presentation == ScanResultPresentationPolicy.Kind.RETRYABLE_FAILURE) {
                            Box(
                                Modifier.size(94.dp).background(
                                    Color(0xFFF05C68).copy(alpha = 0.12f),
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF05C68),
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                            Text("Scan stopped", fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(
                                ScanResultPresentationPolicy.retryMessage(result.remaining),
                                color = Color(0xFF6B6670),
                                textAlign = TextAlign.Center,
                            )
                            if (photoAccess == PhotoAccessLevel.SELECTED) {
                                LimitedAccessNotice()
                            }
                            Button(
                                onClick = { coordinator.scanSelectedEvent() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Try Again", fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onBack) { Text("Done") }
                        } else {
                            Box(
                                Modifier.size(94.dp).background(
                                    Brush.linearGradient(listOf(Color(0xFFF05C68), Color(0xFF8E63F6))),
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                            Text(
                                if (result.checked > 0) "Scan complete" else "You're up to date",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                if (result.checked > 0) {
                                    "Matched photos are now available to the Event members found in them."
                                } else {
                                    "No new photos need scanning for this Event."
                                },
                                color = Color(0xFF6B6670),
                                textAlign = TextAlign.Center,
                            )
                            if (photoAccess == PhotoAccessLevel.SELECTED) {
                                LimitedAccessNotice()
                            }
                            if (presentation == ScanResultPresentationPolicy.Kind.DEFERRED_BATCH) {
                                Button(
                                    onClick = { coordinator.scanSelectedEvent() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Scan Next Batch", fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = onBack) { Text("Done") }
                            } else {
                                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    else -> {
                        Box(
                            Modifier.size(92.dp).background(
                                Brush.linearGradient(listOf(Color(0xFFF05C68), Color(0xFF8E63F6))),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                        Text("Scan Event Photos", fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(
                            "SnapLoop checks only photos within this Event's selected date range.",
                            color = Color(0xFF6B6670),
                            textAlign = TextAlign.Center,
                        )

                        when (photoAccess) {
                            PhotoAccessLevel.SELECTED -> LimitedAccessNotice()
                            PhotoAccessLevel.DENIED -> {
                                Text(
                                    "Photo access is off. Enable photo access in Android Settings to scan this Event.",
                                    color = Color(0xFF6B6670),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                                OutlinedButton(onClick = ::openPhotoSettings, modifier = Modifier.fillMaxWidth()) {
                                    Text("Open Photo Settings", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> Unit
                        }

                        Button(onClick = ::startScan, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Text(
                                if (photoAccess == PhotoAccessLevel.DENIED) "  Check Photo Access" else "  Start Scan",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitedAccessNotice() {
    Text(
        "Selected photos only: SnapLoop can scan only the photos you allowed Android to share with this app. Choose full photo access for complete Event matching.",
        color = Color(0xFF6B6670),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}
