package com.example.myapplication4

import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class ReviewCalendarTest {
    @Test fun currentMonthStartsTodayAndKeepsOverdueInToday() {
        val today = LocalDate.of(2026, 10, 7)
        val period = ReviewCalendarPeriod(YearMonth.from(today), today, ZoneId.of("Africa/Cairo"))
        assertEquals(today, period.dates.first())
        assertEquals(LocalDate.of(2026, 10, 31), period.dates.last())
        assertEquals(Long.MIN_VALUE, period.startMillis)
        assertEquals(Long.MIN_VALUE, calendarDayBounds(today, today, period.zone).first)
        val next = period.copy(month = period.month.plusMonths(1))
        assertEquals(LocalDate.of(2026, 11, 1), next.dates.first())
        assertEquals(next.firstDate.atStartOfDay(next.zone).toInstant().toEpochMilli(), next.startMillis)
        assertTrue(period.copy(month = period.month.minusMonths(1)).dates.isEmpty())
    }

    @Test fun localMidnightsHandleDstAndHalfHourZones() {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 1, 1)
        val spring = calendarDayBounds(LocalDate.of(2026, 3, 8), today, zone)
        val autumn = calendarDayBounds(LocalDate.of(2026, 11, 1), today, zone)
        assertEquals(23 * 3600_000L, spring.second - spring.first)
        assertEquals(25 * 3600_000L, autumn.second - autumn.first)
        val date = LocalDate.of(2026, 10, 8)
        val india = ZoneId.of("Asia/Kolkata")
        val bounds = calendarDayBounds(date, today, india)
        assertEquals(date, Instant.ofEpochMilli(bounds.first).atZone(india).toLocalDate())
        assertEquals(date, Instant.ofEpochMilli(bounds.second - 1).atZone(india).toLocalDate())
        assertEquals(date.plusDays(1), Instant.ofEpochMilli(bounds.second).atZone(india).toLocalDate())
    }

    @Test fun monthGridHasEveryDayOnceIncludingLeapYearAndLocaleWeekOrder() {
        listOf(YearMonth.of(2028, 2), YearMonth.of(2026, 12), YearMonth.of(2027, 1)).forEach { month ->
            listOf(Locale.US, Locale.UK, Locale.forLanguageTag("ar-EG")).forEach { locale ->
                val cells = calendarCells(month, locale)
                assertEquals(0, cells.size % 7)
                assertEquals((1..month.lengthOfMonth()).map { month.atDay(it) }, cells.filterNotNull())
            }
        }
        val month = YearMonth.of(2026, 10) // Thursday begins the month.
        assertEquals(month.atDay(1), calendarCells(month, Locale.US)[4])
        assertEquals(month.atDay(1), calendarCells(month, Locale.UK)[3])
        assertEquals(month.atDay(1), calendarCells(month, Locale.forLanguageTag("ar-EG"))[5])
    }
}
