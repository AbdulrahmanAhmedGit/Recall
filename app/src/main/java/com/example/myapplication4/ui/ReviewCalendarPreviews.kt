package com.example.myapplication4.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.example.myapplication4.domain.ReviewCalendarDay
import com.example.myapplication4.ui.design.RecallSpacing
import com.example.myapplication4.ui.design.RecallTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable private fun CalendarPreview(arabic: Boolean, empty: Boolean = false) {
    val today = LocalDate.of(2026, 10, 7)
    // Design fixture only; the runtime screen always queries actual Room due states.
    val counts = if (empty) emptyMap() else (0L..24L).associate { offset ->
        val day = today.plusDays(offset)
        day to ReviewCalendarDay(day.toEpochDay(), (offset % 5).toInt() * 6, (offset % 5).toInt() * 4,
            (offset % 5).toInt() * 2, 0, 0)
    }
    RecallTheme(darkTheme = arabic) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            androidx.compose.foundation.layout.Box(Modifier.padding(RecallSpacing.md)) {
                ReviewCalendarMonth(YearMonth.from(today), today, counts, today,
                    if (arabic) Locale.forLanguageTag("ar-EG") else Locale.UK, {}, {})
            }
        }
    }
}

@Preview(name = "Review calendar light", widthDp = 420, heightDp = 540)
@Composable private fun LightCalendarPreview() = CalendarPreview(false)

@Preview(name = "Review calendar dark Arabic", widthDp = 420, heightDp = 680, locale = "ar", fontScale = 1.5f)
@Composable private fun ArabicCalendarPreview() = CalendarPreview(true)

@Preview(name = "Empty calendar narrow", widthDp = 320, heightDp = 530)
@Composable private fun EmptyCalendarPreview() = CalendarPreview(false, true)
