package com.snaploop.app.ui

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDateRangeFormatterTest {
    @Test fun `single date uses compact no-year date`() {
        assertEquals(
            "Aug 24",
            EventDateRangeFormatter.format(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24), Locale.US),
        )
    }

    @Test fun `same month keeps both compact endpoints without year`() {
        assertEquals(
            "Aug 24 – Aug 28",
            EventDateRangeFormatter.format(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 28), Locale.US),
        )
    }

    @Test fun `different months keep both compact endpoints without year`() {
        assertEquals(
            "Aug 26 – Sep 10",
            EventDateRangeFormatter.format(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 10), Locale.US),
        )
    }

    @Test fun `different years intentionally omit years on compact Event cards`() {
        assertEquals(
            "Dec 31 – Jan 2",
            EventDateRangeFormatter.format(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2), Locale.US),
        )
    }
}
