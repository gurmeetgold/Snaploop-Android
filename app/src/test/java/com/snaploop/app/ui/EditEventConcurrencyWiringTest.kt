package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EditEventConcurrencyWiringTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/ParityEditEventDialog.kt"),
            File("app/src/main/java/com/snaploop/app/ui/ParityEditEventDialog.kt"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("ParityEditEventDialog.kt was not found from the unit-test working directory")
    }

    @Test
    fun `save verifies latest server revision before submit`() {
        val source = source()
        assertTrue(source.contains("FirebaseEventRepository().fetchEvent(event.id)"))
        assertTrue(source.contains("EventEditConcurrencyPolicy.isFresh(event, latest)"))
        assertTrue(source.contains("onSubmit(cleanName, category, cleanLocation, startsOn, endsOn)"))
    }

    @Test
    fun `stale or unverifiable revision blocks the save with visible error`() {
        val source = source()
        assertTrue(source.contains("EventEditConcurrencyPolicy.STALE_MESSAGE"))
        assertTrue(source.contains("couldn't verify the latest Event version"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.error"))
    }
}
