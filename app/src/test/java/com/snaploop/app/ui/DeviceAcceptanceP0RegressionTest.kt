package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceP0RegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `automatic foreground cadence is thirty minutes and roster changes still bypass`() {
        val policy = source("src/main/java/com/snaploop/app/scanner/AutomaticScanPolicy.kt")
        assertTrue(policy.contains("30L * 60L * 1000L"))
        assertTrue(policy.contains("if (triggerChanged) return true"))
    }

    @Test fun `event range omits year and keeps both endpoints`() {
        val formatter = source("src/main/java/com/snaploop/app/ui/EventDateRangeFormatter.kt")
        assertTrue(formatter.contains("DateTimeFormatter.ofPattern(\"MMM d\""))
        assertFalse(formatter.contains("yyyy"))
        assertTrue(formatter.contains(" – "))
    }

    @Test fun `gallery defaults to three and branded insight is transparent`() {
        val gallery = source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
        assertTrue(gallery.contains("mutableIntStateOf(3)"))
        assertTrue(gallery.contains("SnapGradients.Insight"))
        assertTrue(gallery.contains("containerColor = Color.Transparent"))
    }

    @Test fun `qr scanner has border only rather than white material card`() {
        val qr = source("src/main/java/com/snaploop/app/ui/QrCodeTools.kt")
        assertTrue(qr.contains(".border(2.dp, Color.White.copy(alpha = 0.78f)"))
        assertFalse(qr.contains("fillMaxSize().background(Color.White.copy(alpha = 0.08f))"))
    }

    @Test fun `post auth legacy onboarding gate does not render onboarding again`() {
        val root = source("src/main/java/com/snaploop/app/ui/SnapLoopRoot.kt")
        assertTrue(root.contains("AppGate.ONBOARDING -> if (preAuthOnboardingCompleted && state.user != null)"))
        assertTrue(root.contains("CircularProgressIndicator()"))
    }

    @Test fun `analysis path uses a bitmap without jpeg roundtrip`() {
        val scanner = source("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
        val library = source("src/main/java/com/snaploop/app/media/MediaStorePhotoLibrary.kt")
        val pipeline = source("src/main/java/com/snaploop/app/face/AndroidFacePipeline.kt")
        assertTrue(scanner.contains("library.normalizedBitmap(asset, ANALYSIS_MAX_PIXEL_SIZE)"))
        assertTrue(scanner.contains("ANALYSIS_MAX_PIXEL_SIZE = 1024"))
        assertFalse(scanner.contains("ANALYSIS_JPEG_QUALITY"))
        assertTrue(library.contains("fun normalizedBitmap"))
        assertTrue(pipeline.contains("suspend fun detectFaces(bitmap: Bitmap)"))
    }

    @Test fun `reported safe areas and back labels are explicit`() {
        val privacy = source("src/main/java/com/snaploop/app/ui/ParityPrivacyScreen.kt")
        val scan = source("src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt")
        val gallery = source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
        assertTrue(privacy.contains("statusBarsPadding()"))
        assertTrue(scan.contains("‹ Back"))
        assertTrue(gallery.contains("‹ Back"))
    }

    @Test fun `test my face uses a dedicated result screen`() {
        val face = source("src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt")
        assertTrue(face.contains("ParityFaceTestResultScreen"))
        assertTrue(face.contains("Choose Another Photo"))
        assertTrue(face.contains("Selected Face Setup test photo"))
    }

    @Test fun `left enrollment pose requires deliberate turn`() {
        val tracker = source("src/main/java/com/snaploop/app/ui/GuidedFacePoseTracker.kt")
        assertTrue(tracker.contains("GuidedFacePose.LEFT -> yaw in 36f..55f"))
    }
}
