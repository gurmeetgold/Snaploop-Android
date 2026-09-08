package com.snaploop.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * One-shot, straight-on selfie test. The captured JPEG is handed directly to
 * FaceSetupParityActions and is never saved by this screen.
 */
@Composable
internal fun ParityFaceTestCamera(
    userId: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember(context) { FaceSetupParityActions(context) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var liveState by remember {
        mutableStateOf(
            GuidedFaceLiveState(
                instruction = "Look straight",
                detail = "Center your face inside the frame",
                framingStatus = FaceFramingStatus.NOT_DETECTED,
                poseQualified = false,
            ),
        )
    }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FaceSetupTestResult?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) error = "Camera access is required to test Face Setup."
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted && result == null) {
            GuidedFaceCamera(
                step = 0,
                enabled = !processing,
                processing = processing,
                blockingError = error,
                onLiveState = { liveState = it },
                onCaptured = { jpeg ->
                    if (processing) return@GuidedFaceCamera
                    processing = true
                    error = null
                    scope.launch {
                        runCatching { actions.testSavedFace(userId, jpeg) }
                            .onSuccess { result = it }
                            .onFailure { error = it.message ?: "Face Setup test failed. Try again." }
                        processing = false
                    }
                },
                onError = { error = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("Close", color = Color.White, fontWeight = FontWeight.Bold) }
            Text(
                "Test My Face Setup",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text("Close", color = Color.Transparent)
        }

        if (result == null) {
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (processing) "Checking your Face Setup…" else liveState.instruction,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        error ?: if (processing) {
                            "This test selfie stays on your device and is not saved."
                        } else {
                            liveState.detail
                        },
                        textAlign = TextAlign.Center,
                        color = if (error != null) Color(0xFFB3261E) else Color(0xFF66636C),
                    )
                    if (processing) CircularProgressIndicator()
                    if (error != null) {
                        Button(onClick = { error = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Try Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        result?.let { test ->
            Card(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (test.accepted) "Face Setup is working" else "Face Setup needs attention",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(test.message, textAlign = TextAlign.Center, color = Color(0xFF66636C))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                    if (!test.accepted) {
                        TextButton(onClick = { result = null; error = null }) { Text("Test Again") }
                    }
                }
            }
        }
    }
}
