package com.snaploop.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMatchDeduplicationTest {
    @Test
    fun `modern migration row replaces exact legacy equivalent`() {
        val legacy = match(id = "legacy-id", installation = null, capturedAt = 1_000L, matchedAt = 1_000L)
        val modern = match(id = "event-1:device-a:asset-1", installation = "device-a", capturedAt = 1_000L, matchedAt = 1_100L)

        val unique = PhotoMatchDeduplication.unique(listOf(legacy, modern))

        assertEquals(listOf(modern), unique)
    }

    @Test
    fun `reinstall replay across modern installations collapses exact photo tuple`() {
        val beforeReinstall = match(id = "first", installation = "device-a", capturedAt = 1_000L, matchedAt = 1_100L)
        val afterReinstall = match(id = "second", installation = "device-b", capturedAt = 1_000L, matchedAt = 1_200L)

        val unique = PhotoMatchDeduplication.unique(listOf(beforeReinstall, afterReinstall))

        assertEquals(listOf(afterReinstall), unique)
        assertTrue(PhotoMatchDeduplication.isSamePresentedPhoto(beforeReinstall, afterReinstall))
        assertFalse(PhotoMatchDeduplication.isSameLogicalSourcePhoto(beforeReinstall, afterReinstall))
    }

    @Test
    fun `different capture tuple stays distinct across installations`() {
        val first = match(id = "first", installation = "device-a", capturedAt = 1_000L)
        val second = match(id = "second", installation = "device-b", capturedAt = 1_001L)

        val unique = PhotoMatchDeduplication.unique(listOf(first, second))

        assertEquals(listOf(first, second), unique)
        assertFalse(PhotoMatchDeduplication.isSamePresentedPhoto(first, second))
    }

    @Test
    fun `different asset stays distinct at same capture instant`() {
        val first = match(id = "first", installation = "device-a", capturedAt = 1_000L, assetId = "asset-1")
        val second = match(id = "second", installation = "device-b", capturedAt = 1_000L, assetId = "asset-2")

        assertEquals(listOf(first, second), PhotoMatchDeduplication.unique(listOf(first, second)))
    }

    @Test
    fun `newer replay wins regardless of input order`() {
        val newer = match(id = "newer", installation = "device-b", capturedAt = 1_000L, matchedAt = 2_000L)
        val older = match(id = "older", installation = "device-a", capturedAt = 1_000L, matchedAt = 1_500L)

        assertEquals(listOf(newer), PhotoMatchDeduplication.unique(listOf(newer, older)))
        assertEquals(listOf(newer), PhotoMatchDeduplication.unique(listOf(older, newer)))
    }

    @Test
    fun `modern row wins a matched-time tie with legacy row`() {
        val legacy = match(id = "legacy", installation = null, capturedAt = 1_000L, matchedAt = 1_500L)
        val modern = match(id = "modern", installation = "device-a", capturedAt = 1_000L, matchedAt = 1_500L)

        assertEquals(listOf(modern), PhotoMatchDeduplication.unique(listOf(legacy, modern)))
    }

    @Test
    fun `same installation different capture times remain different presented photos`() {
        val first = match(id = "first", installation = "device-a", capturedAt = 1_000L)
        val second = match(id = "second", installation = "device-a", capturedAt = 1_100L)

        assertEquals(listOf(first, second), PhotoMatchDeduplication.unique(listOf(first, second)))
        assertTrue(PhotoMatchDeduplication.isSameLogicalSourcePhoto(first, second))
        assertFalse(PhotoMatchDeduplication.isSamePresentedPhoto(first, second))
    }

    private fun match(
        id: String,
        installation: String?,
        capturedAt: Long,
        matchedAt: Long = capturedAt + 1,
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
        matchedAtMillis = matchedAt,
        thumbnailPath = "events/event-1/photos/owner-1/photo/thumbnail.jpg",
    )
}
