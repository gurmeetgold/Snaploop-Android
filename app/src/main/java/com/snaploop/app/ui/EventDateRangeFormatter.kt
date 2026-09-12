package com.snaploop.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact Event date-range copy. Event cards intentionally omit the year. */
internal object EventDateRangeFormatter {
    fun format(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
        if (start == end) return start.format(formatter)
        return "${start.format(formatter)} – ${end.format(formatter)}"
    }
}
