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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Face Setup landing page with the same saved-face preview behavior as iOS. */
@Composable
internal fun ParityFaceSetupScreen(
    state: AppUiState,
    onCapture: (ByteArray) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
    onExit: () -> Unit,
) {
    var scanOpen by rememberSaveable { mutableStateOf(false) }

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

    BackHandler(onBack = onExit)
    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(30.dp))
            TextButton(onClick = onExit, modifier = Modifier.align(Alignment.Start)) { Text("‹ Back") }
            Text(
                if (state.user?.hasFaceProfile == true) "Update Your Face" else "Set Up Your Face",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Face Setup enables SnapLoop to find photos of you on participating Event members' phones.",
                textAlign = TextAlign.Center,
                color = androidx.compose.ui.graphics.Color(0xFF66636C),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )

            PremiumCard(Modifier.padding(top = 4.dp)) {
                if (state.user?.hasFaceProfile == true) {
                    FaceReferenceThumbnail(
                        userId = state.user.id,
                        fallbackInitial = state.user.displayName ?: "?",
                        modifier = Modifier.align(Alignment.CenterHorizontally).height(174.dp).fillMaxWidth(0.56f),
                    )
                    Text(
                        "Face Setup Active",
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                    )
                    Text(
                        "Run the guided scan again to replace your current Face Setup.",
                        color = androidx.compose.ui.graphics.Color(0xFF66636C),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp),
                    )
                } else {
                    BrandMark(92)
                    Text(
                        "Five guided angles create your private face template.",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                    )
                }
            }

            PrimaryButton(
                if (state.user?.hasFaceProfile == true) "◎  Update Face Setup" else "◎  Selfie Scan",
                onClick = { onReset(); scanOpen = true },
                modifier = Modifier.padding(top = 18.dp),
            )
            if (state.faceSetupMode == FaceSetupMode.INITIAL_GATE && state.user?.hasFaceProfile != true) {
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
}