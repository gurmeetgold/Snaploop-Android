package com.snaploop.app.ui

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDateRangeFormatterTest {
    @Test fun `single date keeps complete date`() {
        assertEquals(
            "Aug 24, 2026",
            EventDateRangeFormatter.format(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24), Locale.US),
        )
    }

    @Test fun `same month keeps both days and year without duplicate month`() {
        assertEquals(
            "Aug 24–28, 2026",
            EventDateRangeFormatter.format(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 28), Locale.US),
        )
    }

    @Test fun `different months keep complete range and year`() {
        assertEquals(
            "Aug 26 – Sep 10, 2026",
            EventDateRangeFormatter.format(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 10), Locale.US),
        )
    }

    @Test fun `different years retain both years`() {
        assertEquals(
            "Dec 31, 2026 – Jan 2, 2027",
            EventDateRangeFormatter.format(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2), Locale.US),
        )
    }
}
