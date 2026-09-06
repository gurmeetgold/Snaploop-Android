package com.snaploop.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Transitional app root that replaces only Face Setup with the production in-app CameraX flow.
 * All other application gates continue through the existing coordinator-driven UI unchanged.
 */
@Composable
fun SnapLoopRoot(
    activity: Activity,
    coordinator: AppCoordinator,
) {
    val state by coordinator.state.collectAsState()
    if (state.gate != AppGate.FACE_SETUP) {
        SnapLoopApp(activity = activity, coordinator = coordinator)
        return
    }

    AutomaticFaceSetupScreen(state = state, coordinator = coordinator)
}

@Composable
private fun AutomaticFaceSetupScreen(
    state: AppUiState,
    coordinator: AppCoordinator,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var guidance by remember { mutableStateOf("Position your face inside the frame") }
    var completionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraError = if (granted) null else "Camera permission is required to complete Face Setup."
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state.faceCaptures) {
        if (state.faceCaptures >= GuidedFacePose.entries.size && !completionRequested) {
            completionRequested = true
            coordinator.completeFaceSetup()
        }
    }

    val prompts = listOf(
        "Look straight ahead",
        "Turn slightly left",
        "Turn slightly right",
        "Look slightly up",
        "Look slightly down",
    )
    val complete = state.faceCaptures >= prompts.size
    val current = state.faceCaptures.coerceIn(0, prompts.lastIndex)
    val blocker = state.message ?: cameraError

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Face Setup", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(
                if (complete) "Saving your Face Setup…"
                else "Step ${state.faceCaptures + 1} of ${prompts.size} • ${prompts[current]}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (!complete && cameraGranted) {
                GuidedFaceCamera(
                    step = current,
                    enabled = true,
                    processing = state.busy,
                    blockingError = blocker,
                    onGuidance = { guidance = it },
                    onCaptured = { jpeg ->
                        cameraError = null
                        guidance = "Checking capture…"
                        coordinator.addFaceCapture(jpeg)
                    },
                    onError = { error -> cameraError = error },
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(guidance, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Hold still. SnapLoop captures automatically when your pose is ready.")
                        Text(
                            "${state.faceCaptures} of ${prompts.size} captured",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else if (!complete) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(cameraError ?: "Camera access is required for automatic Face Setup.")
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Allow Camera")
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (state.faceCaptures > 0 && !complete) {
                TextButton(
                    onClick = {
                        completionRequested = false
                        guidance = "Position your face inside the frame"
                        cameraError = null
                        coordinator.resetFaceCaptures()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Start Over")
                }
            }

            Text(
                "Your camera stays inside SnapLoop. Five guided captures are checked locally and only accepted when the requested pose is stable.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.busy) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(shape = RoundedCornerShape(24.dp)) {
                    CircularProgressIndicator(Modifier.padding(26.dp))
                }
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

    cameraError?.takeIf { state.message == null && cameraGranted }?.let { error ->
        AlertDialog(
            onDismissRequest = { cameraError = null },
            confirmButton = { TextButton(onClick = { cameraError = null }) { Text("Try Again") } },
            title = { Text("Camera") },
            text = { Text(error) },
        )
    }
}
