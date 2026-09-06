package com.snaploop.app.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EventDateTest {
    @Test fun dateOnlyRangeSurvivesDst() {
        val r=EventDateRange(LocalDate.of(2026,3,7),LocalDate.of(2026,3,9),"America/Toronto")
        assertEquals(3,r.dayCount)
        assertTrue(r.exclusiveEndInstant().isAfter(r.startInstant()))
    }
}
