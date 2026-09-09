package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanResultPresentationPolicyTest {
    @Test
    fun `retryable failures are not presented as a deferred clean batch`() {
        assertEquals(
            ScanResultPresentationPolicy.Kind.RETRYABLE_FAILURE,
            ScanResultPresentationPolicy.kind(remaining = 3, failed = 1),
        )
    }

    @Test
    fun `clean remaining work is presented as next batch`() {
        assertEquals(
            ScanResultPresentationPolicy.Kind.DEFERRED_BATCH,
            ScanResultPresentationPolicy.kind(remaining = 20, failed = 0),
        )
    }

    @Test
    fun `caught up pass is complete`() {
        assertEquals(
            ScanResultPresentationPolicy.Kind.COMPLETE,
            ScanResultPresentationPolicy.kind(remaining = 0, failed = 0),
        )
    }

    @Test
    fun `retry copy preserves pinned singular and plural wording`() {
        assertTrue(ScanResultPresentationPolicy.retryMessage(1).startsWith("1 photo is still pending"))
        assertTrue(ScanResultPresentationPolicy.retryMessage(2).startsWith("2 photos are still pending"))
        assertTrue(ScanResultPresentationPolicy.retryMessage(2).contains("Nothing failed is marked as complete"))
    }
}
