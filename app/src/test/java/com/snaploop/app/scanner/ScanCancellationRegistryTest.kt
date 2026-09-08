package com.snaploop.app.scanner

import kotlinx.coroutines.CancellationException
import org.junit.Assert.fail
import org.junit.Test

class ScanCancellationRegistryTest {
    @Test
    fun `cancel invalidates active token`() {
        val token = ScanCancellationRegistry.start("event-cancel")
        ScanCancellationRegistry.ensureActive(token)
        ScanCancellationRegistry.cancel("event-cancel")

        try {
            ScanCancellationRegistry.ensureActive(token)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `new scan invalidates older scan for same event only`() {
        val old = ScanCancellationRegistry.start("event-same")
        val other = ScanCancellationRegistry.start("event-other")
        val newest = ScanCancellationRegistry.start("event-same")

        try {
            ScanCancellationRegistry.ensureActive(old)
            fail("Expected older scan token to be invalidated")
        } catch (_: CancellationException) {
            // expected
        }

        ScanCancellationRegistry.ensureActive(newest)
        ScanCancellationRegistry.ensureActive(other)
    }
}
