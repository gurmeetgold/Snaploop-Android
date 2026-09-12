package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportedLayoutParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `event dashboard back uses consistent labeled back affordance`() {
        val source = source("ParityEventDashboard.kt")
        assertTrue(source.contains("Text(\"‹ Back\""))
        assertFalse(source.contains("contentDescription = \"Back\")"))
    }

    @Test fun `event hero role is not squeezed beside the full date`() {
        val source = source("ParityEventDashboard.kt")
        assertTrue(source.contains("modifier = Modifier.padding(top = 7.dp)"))
        assertTrue(source.contains("EventDateRangeFormatter.format("))
    }

    @Test fun `gallery cancel stays one line and event count copy matches iOS semantics`() {
        val source = source("ParityPhotoGallery.kt")
        assertTrue(source.contains("softWrap = false"))
        assertTrue(source.contains("photos of you found in this Event"))
    }

    @Test fun `home action cards cap title and subtitle lines`() {
        val source = source("SnapLoopMainShell.kt")
        assertTrue(source.contains("fontSize = 14.sp"))
        assertTrue(source.contains("fontSize = 10.sp"))
        assertTrue(source.contains("EventDateRangeFormatter.format("))
    }
}
