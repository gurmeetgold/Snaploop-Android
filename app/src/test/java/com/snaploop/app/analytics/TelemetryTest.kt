package com.snaploop.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {
    @Test
    fun `scan completion is one aggregate event with bounded scalar properties`() {
        val event = TelemetryEvent.scanCompleted(
            source = "automatic",
            scanned = 500,
            matchedPhotos = 33,
            remaining = 0,
            durationMs = 42_000,
            alreadyCaughtUp = false,
        )

        assertEquals("scan_completed", event.name)
        assertEquals(TelemetryValue.Count(500), event.properties["scanned"])
        assertEquals(TelemetryValue.Count(33), event.properties["matched_photos"])
        assertFalse(event.properties.keys.any { it.contains("photo_id") || it.contains("path") || it.contains("url") })
    }

    @Test
    fun `free-form diagnostic values are reduced to short enum labels`() {
        val event = TelemetryEvent.joinFailed("QR code", "Some / unexpected PRIVATE-looking value !!!")

        assertEquals(TelemetryValue.Text("qr_code"), event.properties["source"])
        assertEquals(
            TelemetryValue.Text("some_unexpected_private_looking_value"),
            event.properties["reason"],
        )
    }

    @Test
    fun `negative counters and durations cannot escape`() {
        val event = TelemetryEvent.scanCompleted(
            source = "manual",
            scanned = -1,
            matchedPhotos = -2,
            remaining = -3,
            durationMs = -4,
            alreadyCaughtUp = true,
        )

        assertEquals(TelemetryValue.Count(0), event.properties["scanned"])
        assertEquals(TelemetryValue.Count(0), event.properties["duration_ms"])
    }

    @Test
    fun `composite fans the same event to all providers`() {
        class FakeSink : TelemetrySink {
            val events = mutableListOf<TelemetryEvent>()
            override fun capture(event: TelemetryEvent) { events += event }
        }

        val first = FakeSink()
        val second = FakeSink()
        val composite = CompositeTelemetrySink(listOf(first, second))
        val event = TelemetryEvent.firstPhotoDiscovered()

        composite.capture(event)

        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
        assertTrue(first.events.single() === second.events.single())
    }
}
