package com.example.myapplication4.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** A projection of completed review logs, not a separately stored activity record. */
data class DailyReviewCount(val epochDay: Long, val reviewCount: Int)

data class DailyActivity(val date: LocalDate, val reviewCount: Int) {
    val level: Int get() = activityLevel(reviewCount)
}

fun activityLevel(reviewCount: Int): Int = when {
    reviewCount <= 0 -> 0
    reviewCount <= 5 -> 1
    reviewCount <= 15 -> 2
    reviewCount <= 30 -> 3
    else -> 4
}

data class ActivityPeriod(val today: LocalDate, val zone: ZoneId) {
    val firstDate: LocalDate get() = today.minusYears(1)
    val startMillis: Long get() = firstDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis: Long get() = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        fun current(): ActivityPeriod {
            val zone = ZoneId.systemDefault()
            return ActivityPeriod(LocalDate.now(zone), zone)
        }
    }
}

data class ActivityWeek(val start: LocalDate, val days: List<DailyActivity?>, val monthStart: LocalDate?)

data class StudyActivity(val period: ActivityPeriod, val counts: Map<LocalDate, Int> = emptyMap()) {
    val totalReviews: Int get() = counts.values.sum()

    fun weeks(locale: Locale): List<ActivityWeek> {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val firstWeek = period.firstDate.with(TemporalAdjusters.previousOrSame(firstDay))
        val lastWeek = period.today.with(TemporalAdjusters.previousOrSame(firstDay))
        return generateSequence(firstWeek) { it.plusWeeks(1) }.takeWhile { it <= lastWeek }.map { start ->
            val days = (0L..6L).map { offset ->
                val date = start.plusDays(offset)
                if (date < period.firstDate || date > period.today) null else DailyActivity(date, counts[date] ?: 0)
            }
            ActivityWeek(start, days, days.filterNotNull().firstOrNull { it.date.dayOfMonth == 1 }?.date
                ?: if (start == firstWeek) period.firstDate else null)
        }.toList()
    }
}

fun activityWeekdays(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}
