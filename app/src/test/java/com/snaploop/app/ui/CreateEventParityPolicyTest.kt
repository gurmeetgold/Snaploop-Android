package com.snaploop.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CreateEventParityPolicyTest {
    private val today = LocalDate.of(2026, 9, 9)

    @Test
    fun `creation window stays exactly fifteen days around today`() {
        val bounds = CreateEventParityPolicy.allowedDates(today)
        assertEquals(LocalDate.of(2026, 8, 25), bounds.lower)
        assertEquals(LocalDate.of(2026, 9, 24), bounds.upper)
        assertTrue(CreateEventParityPolicy.isDateRangeValid(today, today.plusDays(15), today))
        assertFalse(CreateEventParityPolicy.isDateRangeValid(today.minusDays(16), today, today))
        assertFalse(CreateEventParityPolicy.isDateRangeValid(today, today.plusDays(16), today))
    }

    @Test
    fun `maximum duration is capped by mvp and remote configuration`() {
        assertEquals(15, CreateEventParityPolicy.maximumDurationDays(99))
        assertEquals(5, CreateEventParityPolicy.maximumDurationDays(5))
        assertEquals(1, CreateEventParityPolicy.maximumDurationDays(0))

        assertTrue(CreateEventParityPolicy.isDateRangeValid(today, today.plusDays(5), today, 5))
        assertFalse(CreateEventParityPolicy.isDateRangeValid(today, today.plusDays(6), today, 5))
    }

    @Test
    fun `start change preserves a still valid end`() {
        val end = today.plusDays(7)
        assertEquals(
            end,
            CreateEventParityPolicy.repairEndAfterStartChange(
                newStart = today.plusDays(2),
                currentEnd = end,
                today = today,
            ),
        )
    }

    @Test
    fun `start change repairs an end before start to suggested three day duration`() {
        assertEquals(
            today.plusDays(8),
            CreateEventParityPolicy.repairEndAfterStartChange(
                newStart = today.plusDays(5),
                currentEnd = today.plusDays(2),
                today = today,
            ),
        )
    }

    @Test
    fun `start change caps suggested end at creation window upper bound`() {
        assertEquals(
            today.plusDays(15),
            CreateEventParityPolicy.repairEndAfterStartChange(
                newStart = today.plusDays(14),
                currentEnd = today.plusDays(2),
                today = today,
            ),
        )
    }

    @Test
    fun `event name remains pinned to twenty characters`() {
        assertEquals(20, CreateEventParityPolicy.limitName("1234567890123456789012345").length)
        assertEquals("Banff Weekend", CreateEventParityPolicy.limitName("Banff Weekend"))
    }
}
