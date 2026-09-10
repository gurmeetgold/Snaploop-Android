package com.snaploop.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact, lossless date-range copy for narrow Android cards. */
internal object EventDateRangeFormatter {
    fun format(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        if (start == end) return start.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
        return when {
            start.year == end.year && start.month == end.month -> {
                val month = start.format(DateTimeFormatter.ofPattern("MMM", locale))
                "$month ${start.dayOfMonth}–${end.dayOfMonth}, ${start.year}"
            }
            start.year == end.year -> {
                val left = start.format(DateTimeFormatter.ofPattern("MMM d", locale))
                val right = end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
                "$left – $right"
            }
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
                "${start.format(formatter)} – ${end.format(formatter)}"
            }
        }
    }
}
