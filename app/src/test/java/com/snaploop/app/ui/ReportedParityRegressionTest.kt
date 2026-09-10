package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression locks for the September device-parity defects reported from the Redmi build. */
class ReportedParityRegressionTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test
    fun `full screen viewer has icon-only actions and no Not Me overflow`() {
        val viewer = source("ParityFullScreenPhotoViewer.kt")
        assertFalse(viewer.contains("Icons.Filled.MoreVert"))
        assertFalse(viewer.contains("DropdownMenu("))
        assertFalse(viewer.contains("Text(label, color = Color.White"))
        assertTrue(viewer.contains("contentDescription = label"))
    }

    @Test
    fun `invite copy feedback respects the status bar`() {
        val invite = source("ParityShareEventDialog.kt")
        assertTrue(invite.contains(".statusBarsPadding()"))
        assertTrue(invite.contains("feedback?.let { message ->"))
    }

    @Test
    fun `updated Face Setup does not show redundant Done link`() {
        val faceSetup = source("ParityFaceSetupScreen.kt")
        assertFalse(faceSetup.contains("TextButton(onClick = onExit, modifier = Modifier.padding(top = 10.dp)) { Text(\"Done\") }"))
        assertTrue(faceSetup.contains("Text(\"Skip for now\""))
    }

    @Test
    fun `scan completion is retained until explicit Done`() {
        val scan = source("ParityEventScanScreen.kt")
        assertTrue(scan.contains("var retainedResult"))
        assertTrue(scan.contains("val visibleResult = state.scanResult ?: retainedResult"))
        assertTrue(scan.contains("Button(onClick = onBack, modifier = Modifier.fillMaxWidth())"))
    }

    @Test
    fun `replay onboarding stays local to You and returns there`() {
        val you = source("ParityYouScreen.kt")
        assertTrue(you.contains("var replayOnboarding"))
        assertTrue(you.contains("ParityOnboardingScreen(onCompleted = { replayOnboarding = false })"))
        assertFalse(you.contains("coordinator.replayOnboarding()"))
    }
}
