package com.snaploop.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Face Setup landing page with saved-face preview, coverage, test and delete parity. */
@Composable
internal fun ParityFaceSetupScreen(
    state: AppUiState,
    onCapture: (ByteArray) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var scanOpen by rememberSaveable { mutableStateOf(false) }
    var testOpen by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    val hasFaceProfile = state.user?.hasFaceProfile == true

    if (scanOpen) {
        BackHandler { scanOpen = false }
        GuidedFaceEnrollmentCamera(
            captures = state.faceCaptures,
            onCapture = onCapture,
            onReset = onReset,
            onComplete = onComplete,
        )
        return
    }

    if (testOpen) {
        val userId = state.user?.id
        if (userId != null) {
            ParityFaceTestCamera(userId = userId, onClose = { testOpen = false })
            return
        }
        testOpen = false
    }

    BackHandler(onBack = onExit)
    ParityBrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(30.dp))
            TextButton(onClick = onExit, modifier = Modifier.align(Alignment.Start)) { Text("‹ Back") }
            Text(
                if (hasFaceProfile) "Update Your Face" else "Set Up Your Face",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Face Setup enables SnapLoop to find photos of you on participating Event members' phones.",
                textAlign = TextAlign.Center,
                color = Color(0xFF66636C),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )

            ParityPremiumCard(Modifier.padding(top = 4.dp)) {
                if (hasFaceProfile) {
                    FaceReferenceThumbnail(
                        userId = state.user?.id,
                        fallbackInitial = state.user?.displayName ?: "?",
                        modifier = Modifier.align(Alignment.CenterHorizontally).height(174.dp).fillMaxWidth(0.56f),
                    )
                    Text(
                        "Face Setup Active",
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                    )
                    Text(
                        "Selfie coverage 5/5",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF66636C),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
                    )
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "Five useful angles help SnapLoop recognize you across normal Event photos.",
                        color = Color(0xFF66636C),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    )
                } else {
                    ParityBrandMark(92)
                    Text(
                        "Five guided angles create your private face template.",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                    )
                }
            }

            ParityPrimaryButton(
                if (hasFaceProfile) "◎  Selfie Scan" else "◎  Selfie Scan",
                onClick = { onReset(); scanOpen = true },
                modifier = Modifier.padding(top = 18.dp),
            )

            if (hasFaceProfile) {
                OutlinedButton(
                    onClick = { testOpen = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Test My Face Setup", fontWeight = FontWeight.Bold)
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = { deleteConfirmationOpen = true },
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text("Delete Face Setup", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.faceSetupMode == FaceSetupMode.INITIAL_GATE && !hasFaceProfile) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Skip for now", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onExit, modifier = Modifier.padding(top = 10.dp)) { Text("Done") }
            }
            Spacer(Modifier.height(26.dp))
        }
    }

    if (deleteConfirmationOpen && onDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationOpen = false },
            title = { Text("Delete Face Setup?") },
            text = {
                Text(
                    "This removes your saved face template and encrypted local face reference and stops Face Match until you consent and set it up again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmationOpen = false
                        onDelete()
                    },
                ) {
                    Text("Delete Face Setup", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationOpen = false }) { Text("Cancel") }
            },
        )
    }
}
