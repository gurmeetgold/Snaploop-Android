package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSetupReturnNavigationParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `coordinator remembers that update Face Setup came from main`() {
        val source = source("AppCoordinator.kt")
        assertTrue(source.contains("val returnToYou = state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN"))
        assertTrue(source.contains("copy(returnToYouAfterFaceSetup = true)"))
    }

    @Test fun `initial Face Setup does not request You-tab return`() {
        val source = source("AppCoordinator.kt")
        assertTrue(source.contains("if (returnToYou)"))
        assertFalse(source.contains("returnToYouAfterFaceSetup = true,\n                faceSetupMode = FaceSetupMode.INITIAL_GATE"))
    }

    @Test fun `main shell consumes update destination on You tab`() {
        val source = source("SnapLoopMainShell.kt")
        assertTrue(source.contains("if (state.returnToYouAfterFaceSetup)"))
        assertTrue(source.contains("tab = 2"))
        assertTrue(source.contains("coordinator.consumeFaceSetupReturnDestination()"))
    }

    @Test fun `camera closes before slow profile persistence starts`() {
        val source = source("ParityFaceSetupScreen.kt")
        assertTrue(source.contains("scanOpen = false\n                onComplete()"))
    }
}
