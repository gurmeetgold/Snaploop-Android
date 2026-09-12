package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInternalReleaseReadinessTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `production application id matches Play and Firebase registration`() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("com.gurmeetchhiber.snaploop.app"))
        assertTrue(gradle.contains("applicationId = snapLoopApplicationId"))
        assertFalse(gradle.contains("applicationId = \"com.snaploop.app\""))
    }

    @Test fun `release build hard fails when model firebase or upload signing is missing`() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("verifySnapLoopReleaseInputs"))
        assertTrue(gradle.contains("glintr100.onnx"))
        assertTrue(gradle.contains("google-services.json does not contain Android package"))
        assertTrue(gradle.contains("Missing release upload-key configuration"))
        assertTrue(gradle.contains("dependsOn(verifySnapLoopReleaseInputs)"))
    }

    @Test fun `raw face model paths are not exposed to users`() {
        val coordinator = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(coordinator.contains("SnapLoop couldn't start Face Setup"))
        assertTrue(coordinator.contains("text.contains(\"glintr100.onnx\""))
    }
}
