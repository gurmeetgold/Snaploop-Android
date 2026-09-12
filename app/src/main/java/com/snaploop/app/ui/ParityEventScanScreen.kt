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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.snaploop.app.scanner.CameraSyncCoordinator
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
    // loadEvent() refreshes the Event immediately after a scan and currently clears scanResult as
    // part of that refresh. Retain the terminal result locally so the iOS-parity completion screen
    // remains visible until the user explicitly taps Done instead of disappearing by itself.
    var retainedResult by remember(state.selectedEvent?.id) {
        mutableStateOf<CameraSyncCoordinator.Result?>(null)
    }
    LaunchedEffect(state.scanResult) {
        state.scanResult?.let { retainedResult = it }
    }
    val visibleResult = state.scanResult ?: retainedResult

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        PhotoAccessState.markRequested(context)
        photoAccess = PhotoAccessState.current(context)
        if (photoAccess.canRead) {
            retainedResult = null
            coordinator.scanSelectedEvent()
        }
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
        retainedResult = null
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
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ Back", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Scan Photos",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(64.dp))
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SnapGradients.ScanSurface)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                when {
                    state.scanProgress != null -> {
                        val progress = state.scanProgress
                        ScanCircleIcon(Icons.Filled.PhotoLibrary)
                        Text(
                            if (progress.total > 0) {
                                "Scanning ${progress.checked} of ${progress.total} Event photos"
                            } else {
                                "Preparing Event photos…"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
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
                        if (photoAccess == PhotoAccessLevel.SELECTED) LimitedAccessNotice()
                        Text(
                            "Keep SnapLoop open until the scan finishes.",
                            color = Color(0xFF6B6670),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = { state.selectedEvent?.id?.let(ScanCancellationRegistry::cancel) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Stop Scan", fontWeight = FontWeight.Bold)
                        }
                    }

                    visibleResult != null -> {
                        val result = visibleResult
                        val presentation = ScanResultPresentationPolicy.kind(result.remaining, result.failed, result.checked)

                        if (presentation == ScanResultPresentationPolicy.Kind.RETRYABLE_FAILURE) {
                            Box(
                                Modifier.size(82.dp).background(
                                    Color(0xFFF05C68).copy(alpha = 0.12f),
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF05C68),
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                            Text("Scan stopped", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                ScanResultPresentationPolicy.retryMessage(result.remaining),
                                color = Color(0xFF6B6670),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            if (photoAccess == PhotoAccessLevel.SELECTED) LimitedAccessNotice()
                            Button(
                                onClick = {
                                    retainedResult = null
                                    coordinator.scanSelectedEvent()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Try Again", fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onBack) { Text("Done") }
                        } else {
                            Box(
                                Modifier.size(82.dp).background(
                                    Brush.linearGradient(listOf(Color(0xFFF05C68), Color(0xFF8E63F6))),
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                            Text(
                                if (result.checked > 0) "Scan complete" else "You're up to date",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (result.checked > 0) {
                                    "Matched photos are now available to the Event members found in them."
                                } else {
                                    "No new photos need scanning for this Event."
                                },
                                color = Color(0xFF6B6670),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            if (photoAccess == PhotoAccessLevel.SELECTED) LimitedAccessNotice()
                            if (presentation == ScanResultPresentationPolicy.Kind.DEFERRED_BATCH) {
                                Button(
                                    onClick = {
                                        retainedResult = null
                                        coordinator.scanSelectedEvent()
                                    },
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
                        ScanCircleIcon(Icons.Filled.PhotoLibrary)
                        Text("Scan Event Photos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "SnapLoop checks only photos within this Event's selected date range.",
                            color = Color(0xFF6B6670),
                            fontSize = 14.sp,
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

                        Button(onClick = ::startScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
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
}

@Composable
private fun ScanCircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(82.dp).background(
            Brush.linearGradient(listOf(Color(0xFFF05C68), Color(0xFF8E63F6))),
            CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
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
