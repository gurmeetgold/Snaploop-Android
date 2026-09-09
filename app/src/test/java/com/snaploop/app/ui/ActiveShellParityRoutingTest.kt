package com.snaploop.app.ui

import java.io.File
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
}
