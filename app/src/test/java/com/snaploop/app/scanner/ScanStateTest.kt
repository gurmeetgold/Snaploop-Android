package com.snaploop.app.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStateTest {
    @Test fun templateRevisionMarksPriorOutcomeStale() {
        val cursor = RecipientMatchCursor("u", "m1", "f1", "r1")
        cursor.mark("asset", true)
        cursor.reconcile("m1", "f1", "r2")
        assertFalse(cursor.hasEvaluated("asset"))
        assertTrue(cursor.wasMatched("asset"))
    }

    @Test fun membershipGenerationDropsOldOutcomes() {
        val cursor = RecipientMatchCursor("u", "m1", "f1", "r1")
        cursor.mark("asset", true)
        cursor.reconcile("m2", "f1", "r1")
        assertFalse(cursor.wasMatched("asset"))
        assertFalse(cursor.hasEvaluated("asset"))
    }
}
