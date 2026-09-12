package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OvernightDeviceBatchRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `scanner uses bounded fast analysis but preserves publication preview quality`() {
        val scanner = source("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt")
        val pipeline = source("src/main/java/com/snaploop/app/face/AndroidFacePipeline.kt")
        assertTrue(scanner.contains("AndroidFacePipeline(appContext, fastDetection = true)"))
        assertTrue(scanner.contains("ANALYSIS_MAX_PIXEL_SIZE = 1280"))
        assertTrue(scanner.contains("ANALYSIS_JPEG_QUALITY = 84"))
        assertTrue(scanner.contains("config.thumbnailMaxPixelSize"))
        assertTrue(pipeline.contains("fastDetection: Boolean = false"))
    }

    @Test fun `scan and gallery insight cards use branded pink surfaces and scan is centered`() {
        val scan = source("src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt")
        val gallery = source("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
        assertTrue(scan.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertTrue(scan.contains("contentAlignment = Alignment.Center"))
        assertTrue(scan.contains("SnapGradients.ScanSurface"))
        assertFalse(scan.contains("containerColor = Color.White.copy(alpha = 0.98f)"))
        assertTrue(gallery.contains("SnapGradients.Insight"))
    }

    @Test fun `event photo count has one state source and gallery refreshes on entry`() {
        val dashboard = source("src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt")
        val shell = source("src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
        val coordinator = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(dashboard.contains("val photosOfMe = state.photos.size"))
        assertFalse(dashboard.contains("FirebaseMatchRepository().myPhotos"))
        assertTrue(shell.contains("if (page == ShellEventPage.PHOTOS)"))
        assertTrue(shell.contains("coordinator.refreshSelectedEventPhotosInBackground()"))
        assertTrue(coordinator.contains("fun refreshSelectedEventPhotosInBackground()"))
    }

    @Test fun `update Face Setup completion and Back both arm You destination`() {
        val coordinator = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        val routeIndex = coordinator.indexOf("routeAuthenticated(uid)", coordinator.indexOf("fun completeFaceSetup"))
        val flagIndex = coordinator.indexOf("returnToYouAfterFaceSetup = true", coordinator.indexOf("fun completeFaceSetup"))
        assertTrue(flagIndex >= 0 && flagIndex < routeIndex)
        val cancel = coordinator.substring(coordinator.indexOf("fun cancelFaceSetup"), coordinator.indexOf("fun skipFaceSetup"))
        assertTrue(cancel.contains("returnToYouAfterFaceSetup = true"))
    }

    @Test fun `onboarding illustrations are not clipped into narrow square and Back respects navigation inset`() {
        val onboarding = source("src/main/java/com/snaploop/app/ui/ParityOnboardingScreen.kt")
        assertTrue(onboarding.contains("Modifier.fillMaxWidth().height(164.dp)"))
        assertFalse(onboarding.contains("Modifier.size(172.dp)"))
        assertTrue(onboarding.contains(".navigationBarsPadding()"))
        assertTrue(onboarding.contains("modifier = Modifier.height(44.dp)"))
    }

    @Test fun `thumbnail loader remembers direct storage authorization denial`() {
        val loader = source("src/main/java/com/snaploop/app/ui/MatchedThumbnailLoader.kt")
        // Keep this contract behavioral rather than tied to one exact branch expression. The
        // follow-up serializes the first capability probe, but denied recipients must still be
        // remembered and routed directly through the authorized callable fallback thereafter.
        assertTrue(loader.contains("directStorageReadable"))
        assertTrue(loader.contains("StorageException.ERROR_NOT_AUTHORIZED"))
        assertTrue(loader.contains("directStorageReadable = false"))
        assertTrue(loader.contains("authorizedFallback(path)"))
    }
}
