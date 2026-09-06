package com.snaploop.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventContractTest {
    @Test fun canonicalPhotoWindowRevisionIsStable() {
        val event = Event(
            id = "e1", joinCode = "ABCD", inviteToken = "token", creatorUserId = "u1", name = "Trip",
            category = EventCategory.TRIP, coverImagePath = null, locationName = null,
            startsAtMillis = 1_700_000_000_000L, endsAtMillis = 1_700_086_399_999L,
            photoWindowVersion = 1, photoWindowTimeZoneId = "America/Toronto",
            photoWindowStartDayNumber = 20_000, photoWindowEndDayNumber = 20_001,
            status = EventStatus.ACTIVE, createdAtMillis = 1L, updatedAtMillis = 1L,
        )
        assertTrue(event.usesCanonicalPhotoWindow)
        assertEquals("v1:America/Toronto:20000-20001", event.photoWindowRevision)
    }

    @Test fun unknownTimezoneDoesNotClaimCanonicalSemantics() {
        val event = Event(
            id = "e1", joinCode = "ABCD", inviteToken = "token", creatorUserId = "u1", name = "Trip",
            category = EventCategory.TRIP, coverImagePath = null, locationName = null,
            startsAtMillis = 1L, endsAtMillis = 2L, photoWindowVersion = 1, photoWindowTimeZoneId = "Not/AZone",
            photoWindowStartDayNumber = 1, photoWindowEndDayNumber = 1, status = EventStatus.ACTIVE,
            createdAtMillis = 1L, updatedAtMillis = 1L,
        )
        assertFalse(event.usesCanonicalPhotoWindow)
    }
}
