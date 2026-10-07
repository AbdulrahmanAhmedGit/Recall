package com.example.myapplication4.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Current next due dates, not predicted repeats or completed activity. */
data class ReviewCalendarDay(val epochDay: Long, val total: Int, val qa: Int, val cloze: Int, val unseen: Int, val overdue: Int) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

data class ReviewCalendarPeriod(val month: YearMonth, val today: LocalDate, val zone: ZoneId) {
    val firstDate: LocalDate get() = maxOf(month.atDay(1), today)
    val endDate: LocalDate get() = month.plusMonths(1).atDay(1)
    val dates: List<LocalDate> get() = if (firstDate >= endDate) emptyList() else
        generateSequence(firstDate) { it.plusDays(1) }.takeWhile { it < endDate }.toList()
    val includesToday: Boolean get() = YearMonth.from(today) == month
    // In today's month, the Today bucket also includes every overdue card.
    val startMillis: Long get() = if (includesToday) Long.MIN_VALUE else firstDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis: Long get() = endDate.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Local midnight boundaries may be 23/25 hours apart; never add fixed 24-hour milliseconds. */
fun calendarDayBounds(date: LocalDate, today: LocalDate, zone: ZoneId): Pair<Long, Long> =
    (if (date == today) Long.MIN_VALUE else date.atStartOfDay(zone).toInstant().toEpochMilli()) to
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun calendarCells(month: YearMonth, locale: Locale): List<LocalDate?> {
    val start = month.atDay(1)
    val firstWeek = start.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    val leading = java.time.temporal.ChronoUnit.DAYS.between(firstWeek, start).toInt()
    val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    return (0 until cellCount).map { index -> firstWeek.plusDays(index.toLong()).takeIf { YearMonth.from(it) == month } }
}
