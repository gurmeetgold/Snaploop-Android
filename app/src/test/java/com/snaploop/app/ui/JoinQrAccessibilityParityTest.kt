package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinQrAccessibilityParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test
    fun `Join Event exposes heading and assertive validation semantics`() {
        val source = source("ParityJoinEventDialog.kt")
        assertTrue(source.contains("Modifier.semantics { heading() }"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Assertive"))
    }

    @Test
    fun `QR scanner exposes camera close and heading semantics`() {
        val source = source("QrCodeTools.kt")
        assertTrue(source.contains("contentDescription = \"Camera preview for scanning Event QR code\""))
        assertTrue(source.contains("contentDescription = \"Close QR scanner\""))
        assertTrue(source.contains("semantics { heading() }"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Assertive"))
    }

    @Test
    fun `shared QR image has Event specific accessible description`() {
        assertTrue(source("QrCodeTools.kt").contains("contentDescription = \"Event QR code\""))
    }
}
