package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualScanRaceRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `manual scan publishes preparation state before preference IO`() {
        val source = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        val method = source.substring(source.indexOf("fun scanSelectedEvent"), source.indexOf("fun refreshPhotos"))
        val progress = method.indexOf("scanProgress = CameraSyncCoordinator.Progress(0, 0, 0)")
        val preference = method.indexOf("memberPreferences.load(event.id)")
        assertTrue(progress >= 0 && preference >= 0 && progress < preference)
    }

    @Test fun `automatic foreground scanner is cancelled while manual progress exists`() {
        val root = source("src/main/java/com/snaploop/app/ui/SnapLoopRoot.kt")
        assertTrue(root.contains("scanTriggerGeneration, state.scanProgress"))
        assertTrue(root.contains("if (state.scanProgress != null)"))
        assertTrue(root.contains("automaticScanner.cancelActive()"))
        assertTrue(root.contains("current.gate == AppGate.MAIN && current.scanProgress == null"))
    }

    @Test fun `gallery serializes first direct storage authorization probe`() {
        val loader = source("src/main/java/com/snaploop/app/ui/MatchedThumbnailLoader.kt")
        assertTrue(loader.contains("directStorageProbeMutex = Mutex()"))
        assertTrue(loader.contains("directStorageProbeMutex.withLock"))
        assertTrue(loader.contains("private suspend fun directStorageOrFallback"))
    }
}
