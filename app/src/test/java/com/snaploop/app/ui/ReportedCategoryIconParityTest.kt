package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportedCategoryIconParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `home uses meaningful trip and birthday icons instead of placeholders`() {
        val source = source("SnapLoopMainShell.kt")
        assertTrue(source.contains("EventCategory.trip -> Icons.Filled.Flight"))
        assertTrue(source.contains("EventCategory.birthday -> Icons.Filled.Cake"))
        assertFalse(source.contains("EventCategory.birthday -> Icons.Filled.MoreHoriz"))
    }

    @Test fun `event hero uses same trip birthday and party icon language`() {
        val source = source("ParityEventDashboard.kt")
        assertTrue(source.contains("EventCategory.trip -> Icons.Filled.Flight"))
        assertTrue(source.contains("EventCategory.birthday -> Icons.Filled.Cake"))
        assertTrue(source.contains("EventCategory.party -> Icons.Filled.Celebration"))
    }
}
