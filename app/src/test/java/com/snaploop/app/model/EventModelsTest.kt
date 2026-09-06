package com.snaploop.app.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EventModelsTest {
    @Test fun canonicalWindowRequiresVersionAndTimezone() {
        val now = Instant.parse("2026-09-06T00:00:00Z")
        val event = SnapEvent("e","123456","token","u","Trip",startsAt=now,endsAt=now.plusSeconds(86400),photoWindowVersion=1,photoWindowTimeZoneId="America/Toronto",createdAt=now)
        assertTrue(event.usesCanonicalPhotoWindow)
        assertFalse(event.copy(photoWindowTimeZoneId=null).usesCanonicalPhotoWindow)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDateRangeRejected() {
        val now = Instant.now()
        SnapEvent("e","c","t","u","Bad",startsAt=now,endsAt=now.minusSeconds(1),createdAt=now)
    }
}
