package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateEditYouVisualParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `create event uses selected category icon and navigation location icon`() {
        val source = source("ParityCreateEventDialog.kt")
        assertTrue(source.contains("CreateFieldLabel(\"Type\", eventCategoryIcon(category))"))
        assertTrue(source.contains("leadingIcon = {"))
        assertTrue(source.contains("Icons.Filled.Navigation"))
        assertFalse(source.contains("Icons.Filled.Category"))
    }

    @Test fun `edit event uses selected category icon and navigation location icon`() {
        val source = source("ParityEditEventDialog.kt")
        assertTrue(source.contains("EditFieldLabel(\"Type\", eventCategoryIcon(category))"))
        assertTrue(source.contains("eventCategoryIcon(category)"))
        assertTrue(source.contains("Icons.Filled.Navigation"))
        assertFalse(source.contains("Icons.Filled.Category"))
    }

    @Test fun `you uses semantic icons closer to pinned iOS`() {
        val source = source("ParityYouScreen.kt")
        assertTrue(source.contains("Icons.Filled.Badge"))
        assertTrue(source.contains("Icons.Filled.CenterFocusStrong"))
        assertTrue(source.contains("Icons.Filled.Shield"))
        assertTrue(source.contains("Icons.Filled.AutoAwesome"))
        assertFalse(source.contains("Icons.Filled.RestartAlt"))
    }
}
