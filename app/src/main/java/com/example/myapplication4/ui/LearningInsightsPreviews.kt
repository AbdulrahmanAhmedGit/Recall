package com.example.myapplication4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.time.LocalDate
import java.time.ZoneId

@Composable private fun InsightsPreview(empty: Boolean = false) {
    // Preview-only examples. Production statistics always read persisted review data.
    val period = InsightsPeriod(LocalDate.of(2026, 10, 8), ZoneId.of("Africa/Cairo"))
    val history = RecallHistoryAnalysis(period, if (empty) RecallSample() else RecallSample(27, 30, 10))
    Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RecallSpacing.ml),
        verticalArrangement = Arrangement.spacedBy(RecallSpacing.lg)) {
        ObservedRecallSection(history)
        MemoryMaturitySection(if (empty) MemoryCounts() else MemoryCounts(25, 4, 8))
        InsightsExplanation(history)
        PredictedRecallSection(if (empty) CurrentPredictedRecall() else CurrentPredictedRecall(13, 25, .88))
    } }
}

@Preview(name = "Learning insights light", widthDp = 360, heightDp = 760)
@Composable private fun LightInsightsPreview() { RecallTheme(darkTheme = false) { InsightsPreview() } }

@Preview(name = "Learning insights dark RTL", widthDp = 320, heightDp = 760, locale = "ar", fontScale = 1.5f)
@Composable private fun ArabicInsightsPreview() {
    RecallTheme(darkTheme = true) { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { InsightsPreview() } }
}

@Preview(name = "Empty learning insights", widthDp = 320, heightDp = 760)
@Composable private fun EmptyInsightsPreview() { RecallTheme(darkTheme = false) { InsightsPreview(empty = true) } }
