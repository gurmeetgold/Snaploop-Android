package com.snaploop.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JoinEventAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun joinEntryPrimaryControlsAreDiscoverableInSemanticsTree() {
        // Keep this smoke test deterministic on physical devices. The Join surface contains
        // platform/Dialog/TextField work that can keep Compose's auto-advancing clock from
        // reaching quiescence on some OEM builds even though the semantics tree is ready.
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                ParityJoinEventDialog(
                    onDismiss = {},
                    onResolve = {},
                )
            }
        }

        // Produce the initial composition/frame without asking the rule to chase every
        // animation/timer to an idle state.
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Join an Event").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Event code or invite link").assertExists()
        composeRule.onNodeWithText("Continue").assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Scan QR Code").assertExists().assertHasClickAction()
    }
}
