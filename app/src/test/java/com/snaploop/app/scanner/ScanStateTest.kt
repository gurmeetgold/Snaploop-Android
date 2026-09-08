package com.snaploop.app.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStateTest {
    @Test
    fun templateRevisionMarksPriorOutcomeStale() {
        val cursor = RecipientMatchCursor("u", "m1", "f1", "r1")
        cursor.mark("asset", true)
        cursor.reconcile("m1", "f1", "r2")
        assertFalse(cursor.hasEvaluated("asset"))
        assertTrue(cursor.wasMatched("asset"))
    }

    @Test
    fun membershipGenerationDropsOldOutcomes() {
        val cursor = RecipientMatchCursor("u", "m1", "f1", "r1")
        cursor.mark("asset", true)
        cursor.reconcile("m2", "f1", "r1")
        assertFalse(cursor.wasMatched("asset"))
        assertFalse(cursor.hasEvaluated("asset"))
    }

    @Test
    fun reEnablingSelfRecipientReplaysProtectedCorpusWithoutDroppingFaces() {
        val state = ScanState("event")
        val corpus = PhotoCorpusRecord(
            assetId = "asset",
            creationDateMillis = 1L,
            faces = listOf(CachedPhotoFace(floatArrayOf(1f, 0f), 0.2)),
            processedAtMillis = 2L,
        )
        state.photoCorpus["asset"] = corpus
        state.reconcileRecipient("self", "membership", "face", "revision")
        state.markRecipientEvaluation("self", "asset", matched = true)

        // Own matches OFF removes only the self cursor; the protected local photo
        // corpus stays available for a later recipient-only replay.
        state.retainRecipientCursors(emptySet())
        assertFalse(state.recipientCursors.containsKey("self"))
        assertSame(corpus, state.photoCorpus["asset"])

        // Own matches ON recreates a fresh cursor. The already-extracted corpus
        // is pending for that recipient, so CameraSyncCoordinator need not run
        // face extraction again for this cached asset.
        state.reconcileRecipient("self", "membership", "face", "revision")
        assertTrue(state.pendingRecipientUserIds("asset", setOf("self")).contains("self"))
        assertSame(corpus, state.photoCorpus["asset"])
    }
}
