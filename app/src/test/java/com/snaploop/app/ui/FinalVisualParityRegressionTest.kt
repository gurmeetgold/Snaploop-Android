package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalVisualParityRegressionTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$name not found")
    }

    @Test fun `gallery filters use icon semantics and compact typography`() {
        val source = source("ParityPhotoGallery.kt")
        assertTrue(source.contains("leadingIcon = { Icon(Icons.Filled.GridView"))
        assertTrue(source.contains("leadingIcon = { Icon(Icons.Filled.Favorite"))
        assertTrue(source.contains("fontSize = 12.sp"))
    }

    @Test fun `selection toolbar is icon only like iOS`() {
        val source = source("ParityPhotoGallery.kt")
        assertTrue(source.contains("IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp))"))
        assertFalse(source.contains("Text(label, fontSize = 11.sp"))
    }

    @Test fun `full screen viewer exposes no Not Me callback`() {
        val source = source("ParityFullScreenPhotoViewer.kt")
        assertFalse(source.contains("onNotMe:"))
        assertFalse(source.contains("Not Me"))
    }

    @Test fun `gallery density selector is a bordered capsule`() {
        val source = source("ParityPhotoGallery.kt")
        assertTrue(source.contains("shape = RoundedCornerShape(50)"))
        assertTrue(source.contains("border = androidx.compose.foundation.BorderStroke"))
    }
}
