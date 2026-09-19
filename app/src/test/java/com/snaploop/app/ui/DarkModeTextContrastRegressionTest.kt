package com.snaploop.app.ui

import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import java.nio.file.Paths
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
    @Test
    fun `shared backgrounds and cards provide theme-aware foreground colors`() {
        val source = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityUiPrimitives.kt"))
        assert(source.contains("LocalContentColor provides MaterialTheme.colorScheme.onBackground"))
        assert(source.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
    }

    @Test
    fun `reported dark-mode headings and fields use theme colors`() {
        val face = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityFaceSetupScreen.kt"))
        assert(face.contains("color = MaterialTheme.colorScheme.onBackground"))

        val name = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityNameSetupScreen.kt"))
        assert(name.contains("color = MaterialTheme.colorScheme.onBackground"))
        assert(name.contains("Text(\"Your name\", color = MaterialTheme.colorScheme.onSurfaceVariant)"))
    }

    @Test
    fun `face match consent has no redundant privacy heading`() {
        val consent = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityConsentScreen.kt"))
        assert(!consent.contains("Text(\"Privacy\""))
    }

}
