package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditEventVisualParityTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/ParityEditEventDialog.kt"),
            File("app/src/main/java/com/snaploop/app/ui/ParityEditEventDialog.kt"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("ParityEditEventDialog.kt was not found from the unit-test working directory")
    }

    @Test
    fun `edit text fields use the pinned iOS filled rounded treatment`() {
        val source = source()
        assertTrue(source.contains("EditFilledTextField("))
        assertTrue(source.contains("shape = RoundedCornerShape(14.dp)"))
        assertTrue(source.contains("focusedIndicatorColor = Color.Transparent"))
        assertFalse(source.contains("OutlinedTextField("))
    }

    @Test
    fun `event type uses menu style instead of outlined selector`() {
        val source = source()
        assertTrue(source.contains("TextButton("))
        assertTrue(source.contains("DropdownMenu("))
        assertTrue(source.contains("color = SnapColors.Coral"))
    }

    @Test
    fun `saving keeps Save Event copy and swaps checkmark for spinner while controls are busy`() {
        val source = source()
        assertTrue(source.contains("text = \"Save Event\""))
        assertTrue(source.contains("val controlsBusy = busy || checkingRevision"))
        assertTrue(source.contains("if (controlsBusy)"))
        assertTrue(source.contains("CircularProgressIndicator("))
        assertFalse(source.contains("if (busy) \"Saving…\" else \"Save Event\""))
    }

    @Test
    fun `name and location inputs use word capitalization like iOS`() {
        assertTrue(
            source().contains(
                "KeyboardOptions(capitalization = KeyboardCapitalization.Words)",
            ),
        )
    }
}
