package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class YouAccessibilityParityTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/ParityYouScreen.kt"),
            File("app/src/main/java/com/snaploop/app/ui/ParityYouScreen.kt"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("ParityYouScreen.kt was not found from the unit-test working directory")
    }

    @Test
    fun `You title is exposed as an accessibility heading`() {
        assertTrue(source().contains("modifier = Modifier.semantics { heading() }"))
    }

    @Test
    fun `clickable You rows merge decorative descendants for TalkBack`() {
        val source = source()
        val marker = "semantics(mergeDescendants = true) { }"
        val mergeCount = source.windowed(marker.length, 1).count { it == marker }
        assertTrue("Expected account rows and menu cards to merge accessibility descendants", mergeCount >= 4)
    }
}
