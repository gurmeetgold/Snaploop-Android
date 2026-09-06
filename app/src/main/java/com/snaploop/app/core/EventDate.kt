package com.snaploop.app.core

import java.time.LocalDate
import java.time.ZoneId

data class EventDateRange(val start: LocalDate, val endInclusive: LocalDate, val timeZoneId: String) {
    init { require(!endInclusive.isBefore(start)); ZoneId.of(timeZoneId) }
    val dayCount: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) + 1
    fun startInstant() = start.atStartOfDay(ZoneId.of(timeZoneId)).toInstant()
    fun exclusiveEndInstant() = endInclusive.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toInstant()
    fun containsEpochMillis(ms: Long): Boolean = ms >= startInstant().toEpochMilli() && ms < exclusiveEndInstant().toEpochMilli()
}
