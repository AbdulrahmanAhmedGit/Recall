package com.example.myapplication4.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.StudyActivityCard
import com.example.myapplication4.ui.design.RecallSpacing
import com.example.myapplication4.ui.design.RecallTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private fun previewActivity(): StudyActivity {
    val today = LocalDate.of(2026, 10, 6)
    // Preview-only fixture. Runtime always reads Room review logs.
    return StudyActivity(ActivityPeriod(today, ZoneId.of("Africa/Cairo")), (0L..110L)
        .filter { it % 5 != 0L }.associate { today.minusDays(it) to listOf(2, 9, 23, 38)[(it % 4).toInt()] })
}

@Preview(name = "Activity light", widthDp = 360, heightDp = 390)
@Composable private fun LightActivityPreview() {
    RecallTheme(darkTheme = false) { StudyActivityCard(previewActivity(), Modifier.padding(RecallSpacing.md), Locale.US) }
}

@Preview(name = "Activity dark Arabic", widthDp = 360, heightDp = 410, locale = "ar", fontScale = 1.3f)
@Composable private fun ArabicActivityPreview() {
    RecallTheme(darkTheme = true) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            StudyActivityCard(previewActivity(), Modifier.padding(RecallSpacing.md), Locale.forLanguageTag("ar-EG"))
        }
    }
}

@Preview(name = "Empty activity small phone", widthDp = 320, heightDp = 430)
@Composable private fun EmptyActivityPreview() {
    RecallTheme(darkTheme = false) { StudyActivityCard(previewActivity().copy(counts = emptyMap()), Modifier.padding(RecallSpacing.md), Locale.UK) }
}
