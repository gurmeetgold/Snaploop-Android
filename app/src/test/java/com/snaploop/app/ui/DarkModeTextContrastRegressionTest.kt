package com.snaploop.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkModeTextContrastRegressionTest {
    @Test
    fun `dark mode always resolves readable text to pure white`() {
        assertEquals(Color.White, resolveSnapTextColor(true, SnapColors.Ink))
        assertEquals(Color.White, resolveSnapTextColor(true, SnapColors.Secondary))
        assertEquals(Color.White, resolveSnapTextColor(true, SnapColors.Coral))
        assertEquals(Color.White, resolveSnapTextColor(true, Color.Red))
    }

    @Test
    fun `light mode preserves the exact requested text color`() {
        val samples = listOf(
            SnapColors.Ink,
            SnapColors.Secondary,
            SnapColors.Coral,
            SnapColors.Lilac,
            Color.Red,
            Color(0xFF66636C),
        )
        samples.forEach { color ->
            assertEquals(color, resolveSnapTextColor(false, color))
        }
    }
}
