package com.example.myapplication4

import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class StudyActivityTest {
    @Test fun fixedLevelsDoNotNormalizeAgainstOtherDays() {
        val expected = mapOf(0 to 0, 1 to 1, 5 to 1, 6 to 2, 15 to 2, 16 to 3, 30 to 3, 31 to 4, 1000 to 4)
        expected.forEach { (count, level) -> assertEquals(level, activityLevel(count)) }
        assertEquals(0, activityLevel(-1))
    }

    @Test fun emptyYearIncludesCurrentPartialWeekAndNoFutureDays() {
        val today = LocalDate.of(2026, 10, 6)
        val model = StudyActivity(ActivityPeriod(today, ZoneId.of("Africa/Cairo")))
        val weeks = model.weeks(Locale.UK)
        assertTrue(weeks.size in 53..54)
        assertEquals(LocalDate.of(2026, 10, 5), weeks.last().start)
        assertEquals(listOf(today.minusDays(1), today), weeks.last().days.filterNotNull().map { it.date })
        assertEquals(5, weeks.last().days.count { it == null })
        assertEquals(0, model.totalReviews)
        assertTrue(weeks.flatMap { it.days }.filterNotNull().all { it.level == 0 })
        assertEquals(model.period.firstDate, weeks.first().days.filterNotNull().first().date)
    }

    @Test fun historyRetainsExactCountsAcrossMonthAndYearBoundaries() {
        val dates = listOf(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1))
        val model = StudyActivity(ActivityPeriod(LocalDate.of(2026, 2, 3), ZoneId.of("UTC")), dates.zip(listOf(5, 6, 30, 31)).toMap())
        val days = model.weeks(Locale.US).flatMap { it.days }.filterNotNull().associateBy { it.date }
        dates.forEach { assertEquals(model.counts[it], days[it]?.reviewCount) }
        assertEquals(72, model.totalReviews)
        assertEquals(4, days[dates.last()]?.level)
        val monthLabels = model.weeks(Locale.US).mapNotNull { it.monthStart }
        assertTrue(monthLabels.contains(LocalDate.of(2026, 1, 1)))
        assertTrue(monthLabels.contains(LocalDate.of(2026, 2, 1)))
        assertEquals(monthLabels.size, monthLabels.map { it.withDayOfMonth(1) }.distinct().size)
    }

    @Test fun localeChangesWeekRowsWithoutReversingCalendarDates() {
        val model = StudyActivity(ActivityPeriod(LocalDate.of(2026, 10, 6), ZoneId.of("Africa/Cairo")))
        assertEquals(DayOfWeek.MONDAY, activityWeekdays(Locale.UK).first())
        assertEquals(DayOfWeek.SUNDAY, activityWeekdays(Locale.US).first())
        val arabic = Locale.forLanguageTag("ar-EG")
        assertEquals(DayOfWeek.SATURDAY, activityWeekdays(arabic).first())
        val weeks = model.weeks(arabic)
        assertEquals(DayOfWeek.SATURDAY, weeks.last().start.dayOfWeek)
        val dates = weeks.flatMap { it.days }.filterNotNull().map { it.date }
        assertEquals(dates.sorted(), dates)
        assertEquals(model.period.today, dates.last())
        assertNotEquals("October", java.time.Month.OCTOBER.getDisplayName(java.time.format.TextStyle.FULL, arabic))
    }

    @Test fun rangeUsesRealLocalMidnightsIncludingDst() {
        val spring = ActivityPeriod(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York"))
        val start = spring.today.atStartOfDay(spring.zone).toInstant().toEpochMilli()
        assertEquals(23 * 60 * 60 * 1000L, spring.endMillis - start)
        val autumn = ActivityPeriod(LocalDate.of(2026, 11, 1), spring.zone)
        val autumnStart = autumn.today.atStartOfDay(autumn.zone).toInstant().toEpochMilli()
        assertEquals(25 * 60 * 60 * 1000L, autumn.endMillis - autumnStart)
        val india = ActivityPeriod(LocalDate.of(2026, 10, 6), ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDate.of(2026, 10, 6), java.time.Instant.ofEpochMilli(india.endMillis - 1).atZone(india.zone).toLocalDate())
    }
}
