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
        composeRule.setContent {
            MaterialTheme {
                ParityJoinEventDialog(
                    onDismiss = {},
                    onResolve = {},
                )
            }
        }

        composeRule.onNodeWithText("Join an Event").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Event code or invite link").assertExists()
        composeRule.onNodeWithText("Continue").assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Scan QR Code").assertExists().assertHasClickAction()
    }
}
