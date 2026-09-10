from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected source exactly once, found {count}")
    path.write_text(text.replace(old, new, 1))


coordinator = Path("app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
replace_once(
    coordinator,
    '''    val faceCaptures: Int = 0,
    val faceSetupMode: FaceSetupMode = FaceSetupMode.INITIAL_GATE,
)''',
    '''    val faceCaptures: Int = 0,
    val faceSetupMode: FaceSetupMode = FaceSetupMode.INITIAL_GATE,
    val returnToYouAfterFaceSetup: Boolean = false,
)''',
    "Face Setup return destination state",
)
replace_once(
    coordinator,
    '''    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        require(pendingFaceEmbeddings.size == FaceModelPolicy.TARGET_TEMPLATE_COUNT) {''',
    '''    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        val returnToYou = state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN
        require(pendingFaceEmbeddings.size == FaceModelPolicy.TARGET_TEMPLATE_COUNT) {''',
    "Capture Face Setup launch origin",
)
replace_once(
    coordinator,
    '''        users.syncMyProfile(uid, state.value.user?.displayName)
        routeAuthenticated(uid)
    }

    fun refreshEvents()''',
    '''        users.syncMyProfile(uid, state.value.user?.displayName)
        routeAuthenticated(uid)
        if (returnToYou) {
            update { copy(returnToYouAfterFaceSetup = true) }
        }
    }

    fun consumeFaceSetupReturnDestination() {
        update { copy(returnToYouAfterFaceSetup = false) }
    }

    fun refreshEvents()''',
    "Publish Face Setup return destination",
)

shell = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
replace_once(
    shell,
    '''    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(''',
    '''    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.returnToYouAfterFaceSetup) {
        if (state.returnToYouAfterFaceSetup) {
            coordinator.dismissPendingInvite()
            coordinator.closeEvent()
            tab = 2
            coordinator.consumeFaceSetupReturnDestination()
        }
    }

    Scaffold(''',
    "Return updated Face Setup to You tab",
)


test_path = Path("app/src/test/java/com/snaploop/app/ui/FaceSetupReturnNavigationParityTest.kt")
test_path.write_text('''package com.snaploop.app.ui

import java.io.File
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

    @Test fun `main shell consumes update destination on You tab`() {
        val source = source("SnapLoopMainShell.kt")
        assertTrue(source.contains("if (state.returnToYouAfterFaceSetup)"))
        assertTrue(source.contains("tab = 2"))
        assertTrue(source.contains("coordinator.consumeFaceSetupReturnDestination()"))
    }

    @Test fun `camera closes before slow profile persistence starts`() {
        val source = source("ParityFaceSetupScreen.kt")
        assertTrue(source.contains("scanOpen = false\\n                onComplete()"))
    }
}
''')
