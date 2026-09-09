package com.snaploop.app.ui

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure Create Event date/name semantics mirrored from pinned iOS CreateEventView/EventLifecycle. */
internal object CreateEventParityPolicy {
    const val MAX_NAME_CHARACTERS = 20
    const val DATE_WINDOW_DAYS = 15
    const val MVP_MAX_DURATION_DAYS = 15
    const val SUGGESTED_DURATION_DAYS = 3

    data class DateBounds(
        val lower: LocalDate,
        val upper: LocalDate,
    )

    fun limitName(value: String): String = value.take(MAX_NAME_CHARACTERS)

    fun allowedDates(today: LocalDate): DateBounds = DateBounds(
        lower = today.minusDays(DATE_WINDOW_DAYS.toLong()),
        upper = today.plusDays(DATE_WINDOW_DAYS.toLong()),
    )

    fun maximumDurationDays(configuredMaxDays: Int = MVP_MAX_DURATION_DAYS): Int =
        minOf(MVP_MAX_DURATION_DAYS, configuredMaxDays.coerceAtLeast(1))

    fun allowedEndUpper(
        startsOn: LocalDate,
        today: LocalDate,
        configuredMaxDays: Int = MVP_MAX_DURATION_DAYS,
    ): LocalDate {
        val creationUpper = allowedDates(today).upper
        val durationUpper = startsOn.plusDays(maximumDurationDays(configuredMaxDays).toLong())
        return minOf(creationUpper, durationUpper)
    }

    /**
     * Pinned iOS preserves the existing end date when it remains legal. If the new start makes the
     * end illegal, it repairs to Start + 3 days, capped by both the creation window and max duration.
     */
    fun repairEndAfterStartChange(
        newStart: LocalDate,
        currentEnd: LocalDate,
        today: LocalDate,
        configuredMaxDays: Int = MVP_MAX_DURATION_DAYS,
    ): LocalDate {
        val upper = allowedEndUpper(newStart, today, configuredMaxDays)
        if (!currentEnd.isBefore(newStart) && !currentEnd.isAfter(upper)) return currentEnd
        return minOf(upper, newStart.plusDays(SUGGESTED_DURATION_DAYS.toLong()))
    }

    fun isDateRangeValid(
        startsOn: LocalDate,
        endsOn: LocalDate,
        today: LocalDate,
        configuredMaxDays: Int = MVP_MAX_DURATION_DAYS,
    ): Boolean {
        val bounds = allowedDates(today)
        if (startsOn.isBefore(bounds.lower) || startsOn.isAfter(bounds.upper)) return false
        if (endsOn.isBefore(bounds.lower) || endsOn.isAfter(bounds.upper)) return false
        if (endsOn.isBefore(startsOn)) return false
        val distance = ChronoUnit.DAYS.between(startsOn, endsOn)
        return distance in 0..maximumDurationDays(configuredMaxDays).toLong()
    }
}
