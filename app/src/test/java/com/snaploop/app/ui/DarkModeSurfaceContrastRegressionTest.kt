package com.snaploop.app.ui

import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkModeSurfaceContrastRegressionTest {
    @Test
    fun `light surface text remains dark in either appearance`() {
        assertEquals(SnapColors.Ink, snapTextOnLightSurface())
        assertEquals(SnapColors.Secondary, snapTextOnLightSurface(SnapColors.Secondary))
        assertEquals(Color.Red, snapTextOnLightSurface(Color.Red))
    }

    @Test
    fun `onboarding light banners use dark foreground tokens`() {
        val source = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityOnboardingScreen.kt"))
        assertTrue(source.contains("color = snapTextOnLightSurface(SnapColors.Ink.copy(alpha = 0.78f))"))
        assertTrue(source.contains("Text(label, color = snapTextOnLightSurface()"))
        assertTrue(source.contains("color = snapTextOnLightSurface(), maxLines = 1"))
        assertFalse(source.contains("color = snapReadableTextColor(SnapColors.Ink.copy(alpha = 0.78f))"))
    }

    @Test
    fun `scan light gradient uses dark foreground tokens`() {
        val source = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityEventScanScreen.kt"))
        assertTrue(source.contains(".background(SnapGradients.ScanSurface)"))
        assertTrue(source.contains("color = snapTextOnLightSurface()"))
        assertTrue(source.contains("color = snapTextOnLightSurface(SnapColors.Secondary)"))
    }

    @Test
    fun `legacy fallback shell also uses theme-aware surfaces`() {
        val source = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/SnapLoopApp.kt"))
        assertTrue(source.contains("NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))"))
        assertTrue(source.contains("CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))"))
        assertTrue(source.contains("listOf(SnapColors.DarkCanvas, SnapColors.DarkSurface, SnapColors.DarkCanvas)"))
        assertFalse(source.contains("CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f))"))
    }

    @Test
    fun `you and gallery avoid light cards with white dark-mode text`() {
        val you = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityYouScreen.kt"))
        assertTrue(you.contains("Brush.linearGradient(listOf(SnapColors.DarkCanvas, SnapColors.DarkSubtleSurface))"))

        val gallery = Files.readString(Paths.get("src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt"))
        assertTrue(gallery.contains("CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)"))
    }
}
