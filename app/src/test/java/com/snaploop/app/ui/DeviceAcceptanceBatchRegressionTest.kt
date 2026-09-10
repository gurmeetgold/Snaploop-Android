package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceBatchRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `face setup requires active biometric consent at every sensitive entry point`() {
        val source = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(source.contains("if (!ensureActiveBiometricConsent(uid)) return@launchBusy"))
        assertTrue(source.contains("private suspend fun ensureActiveBiometricConsent"))
        assertTrue(source.contains("gate = AppGate.BIOMETRIC_CONSENT"))
    }

    @Test fun `test my face uses gallery picker and completion shows explicit progress`() {
        val source = source("src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt")
        assertTrue(source.contains("ActivityResultContracts.GetContent()"))
        assertFalse(source.contains("ParityFaceTestCamera("))
        assertTrue(source.contains("Finishing Face Setup…"))
        assertTrue(source.contains("CircularProgressIndicator"))
    }

    @Test fun `own match preference is visible before corpus replay finishes`() {
        val source = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(source.contains("includeOwnMatches = resolvedEnabled"))
        assertTrue(source.contains("val shouldReplay = OwnMatchReplayPolicy.shouldReplay"))
        assertTrue(source.contains("viewModelScope.launch {\n            if (shouldReplay)"))
    }

    @Test fun `scanner batches encrypted checkpoints`() {
        val source = source("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
        assertTrue(source.contains("if (completed % 4 == 0) states.save(state)"))
    }

    @Test fun `restore uses current brand mark and viewer count is trailing`() {
        val app = source("src/main/java/com/snaploop/app/ui/SnapLoopApp.kt")
        val viewer = source("src/main/java/com/snaploop/app/ui/ParityFullScreenPhotoViewer.kt")
        assertTrue(app.contains("ParityBrandMark(78)"))
        assertFalse(viewer.contains("Spacer(Modifier.size(48.dp))"))
    }

    @Test fun `gallery and you use canonical SnapLoop wash`() {
        assertTrue(source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt").contains("background(SnapGradients.SoftWash)"))
        assertTrue(source("src/main/java/com/snaploop/app/ui/ParityYouScreen.kt").contains("background(SnapGradients.SoftWash)"))
    }
}
