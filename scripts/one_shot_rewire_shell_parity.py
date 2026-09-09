from pathlib import Path

shell_path = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
text = shell_path.read_text()

old_join = """    if (joinOpen) {
        ShellJoinDialog(
            onDismiss = { joinOpen = false },
            onResolve = {
                joinOpen = false
                coordinator.resolveJoinInput(it)
            },
        )
    }"""
new_join = """    if (joinOpen) {
        ParityJoinEventDialog(
            onDismiss = { joinOpen = false },
            onResolve = {
                joinOpen = false
                coordinator.resolveJoinInput(it)
            },
        )
    }"""

if text.count(old_join) != 1:
    raise SystemExit("manual Join Event route no longer matches the expected legacy source exactly once")
text = text.replace(old_join, new_join, 1)
shell_path.write_text(text)

test_path = Path("app/src/test/java/com/snaploop/app/ui/ActiveShellParityRoutingTest.kt")
test_path.write_text("""package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveShellParityRoutingTest {
    private fun shellSource(): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt"),
            File("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("SnapLoopMainShell.kt was not found from the unit-test working directory")
    }

    @Test
    fun `pending invitations render the dedicated parity review surface`() {
        assertTrue(
            shellSource().contains(
                "state.pendingInvite != null -> ParityInvitationReviewScreen(state, coordinator)",
            ),
        )
    }

    @Test
    fun `event invite page renders the dedicated parity share surface`() {
        val source = shellSource()
        assertTrue(source.contains("ShellEventPage.INVITE -> ParityShareEventDialog("))
        assertTrue(source.contains("canManageInvites = shellCurrentRole(event, state.members, state.user?.id)"))
    }

    @Test
    fun `home manual join uses the dedicated pinned parity entry surface`() {
        val source = shellSource()
        assertTrue(source.contains("if (joinOpen) {\\n        ParityJoinEventDialog("))
        assertFalse(source.contains("if (joinOpen) {\\n        ShellJoinDialog("))
    }
}
""")
