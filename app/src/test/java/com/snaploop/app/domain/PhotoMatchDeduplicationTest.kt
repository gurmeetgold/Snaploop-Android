package com.snaploop.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMatchDeduplicationTest {
    @Test
    fun `modern migration row replaces exact legacy equivalent`() {
        val legacy = match(id = "legacy-id", installation = null, capturedAt = 1_000L)
        val modern = match(id = "event-1:device-a:asset-1", installation = "device-a", capturedAt = 1_000L)

        val unique = PhotoMatchDeduplication.unique(listOf(legacy, modern))

        assertEquals(listOf(modern), unique)
    }

    @Test
    fun `different modern source installations stay distinct`() {
        val first = match(id = "first", installation = "device-a", capturedAt = 1_000L)
        val second = match(id = "second", installation = "device-b", capturedAt = 1_000L)

        val unique = PhotoMatchDeduplication.unique(listOf(first, second))

        assertEquals(listOf(first, second), unique)
    }

    @Test
    fun `legacy row remains when capture tuple differs`() {
        val legacy = match(id = "legacy-id", installation = null, capturedAt = 900L)
        val modern = match(id = "modern-id", installation = "device-a", capturedAt = 1_000L)

        val unique = PhotoMatchDeduplication.unique(listOf(legacy, modern))

        assertEquals(listOf(legacy, modern), unique)
    }

    @Test
    fun `duplicate logical source keeps first row`() {
        val first = match(id = "first", installation = "device-a", capturedAt = 1_000L)
        val duplicate = match(id = "duplicate", installation = "device-a", capturedAt = 1_100L)

        val unique = PhotoMatchDeduplication.unique(listOf(first, duplicate))

        assertEquals(listOf(first), unique)
        assertTrue(PhotoMatchDeduplication.isSameLogicalSourcePhoto(first, duplicate))
    }

    @Test
    fun `legacy and modern rows are different logical sources outside migration suppression`() {
        val legacy = match(id = "legacy-id", installation = null, capturedAt = 900L)
        val modern = match(id = "modern-id", installation = "device-a", capturedAt = 1_000L)

        assertFalse(PhotoMatchDeduplication.isSameLogicalSourcePhoto(legacy, modern))
    }

    private fun match(
        id: String,
        installation: String?,
        capturedAt: Long,
        assetId: String = "asset-1",
        ownerId: String = "owner-1",
    ) = PhotoMatch(
        id = id,
        eventId = "event-1",
        ownerUserId = ownerId,
        sourceInstallationId = installation,
        sourceMembershipId = null,
        assetLocalId = assetId,
        appearances = emptyList(),
        capturedAtMillis = capturedAt,
        matchedAtMillis = capturedAt + 1,
        thumbnailPath = "events/event-1/photos/owner-1/photo/thumbnail.jpg",
    )
}
