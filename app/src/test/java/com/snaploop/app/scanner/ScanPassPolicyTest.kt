package com.snaploop.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPassPolicyTest {
    @Test
    fun `failed attempted assets remain pending with deferred tail`() {
        assertEquals(
            5,
            ScanPassPolicy.remainingAfterPass(
                pendingBeforePass = 10,
                attemptedThisPass = 8,
                failedThisPass = 3,
            ),
        )
    }

    @Test
    fun `successful bounded pass reports only deferred work`() {
        assertEquals(
            2,
            ScanPassPolicy.remainingAfterPass(
                pendingBeforePass = 10,
                attemptedThisPass = 8,
                failedThisPass = 0,
            ),
        )
    }

    @Test
    fun `all successful attempted work can be caught up`() {
        assertEquals(
            0,
            ScanPassPolicy.remainingAfterPass(
                pendingBeforePass = 4,
                attemptedThisPass = 4,
                failedThisPass = 0,
            ),
        )
    }

    @Test
    fun `automatic checkpoint is withheld while retryable failures remain`() {
        assertFalse(ScanPassPolicy.shouldCheckpointAutomaticPass(failedThisPass = 1))
        assertFalse(ScanPassPolicy.shouldCheckpointAutomaticPass(failedThisPass = 4))
        assertTrue(ScanPassPolicy.shouldCheckpointAutomaticPass(failedThisPass = 0))
    }
}
