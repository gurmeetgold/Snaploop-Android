package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalStabilizationRegressionTest {
    @Test
    fun galleryHasPullToRefreshAndScannerUsesFastBoundedInput() {
        val gallery = File("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt").readText()
        val scanner = File("src/main/java/com/snaploop/app/scanner/CameraSyncCoordinator.kt").readText()
        assertTrue(gallery.contains("PullToRefreshBox"))
        assertTrue(gallery.contains("onRefresh = ::requestRefresh"))
        assertTrue(scanner.contains("ANALYSIS_MAX_PIXEL_SIZE = 768"))
        assertTrue(scanner.contains("completed % 12 == 0"))
    }
}
