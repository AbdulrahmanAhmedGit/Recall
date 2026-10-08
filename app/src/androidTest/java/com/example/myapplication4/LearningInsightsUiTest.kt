package com.example.myapplication4

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import com.example.myapplication4.util.RecallStrings
import com.example.myapplication4.util.RecallLocale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class LearningInsightsUiTest {
    @get:Rule val compose = createComposeRule()
    private val period = InsightsPeriod(LocalDate.of(2026, 10, 8), ZoneId.of("Africa/Cairo"))
    private val history = RecallHistoryAnalysis(period, RecallSample(27, 30, 10), RecallSample(24, 30, 10), 4, 1)

    @Test fun cairoArabicThemePreservesMixedScienceTextAndLargeFontLayout() {
        var family: androidx.compose.ui.text.font.FontFamily? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, 1.5f)) {
                // Arabic selected in the app, even when the device/context is English.
                RecallTheme(darkTheme = true, language = "ar") {
                    val font = androidx.compose.material3.MaterialTheme.typography.bodyLarge.fontFamily
                    SideEffect { family = font }
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(RecallSpacing.md)) {
                        BidiAwareText("راجع درس Electric Current اليوم", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        BidiAwareText("توزيع Mn²⁺ هو [[chem:[Ar] 3d⁵]]", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        BidiAwareText("قانون Ohm هو [[math:V = IR]]", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        PredictedRecallSection(CurrentPredictedRecall(1, 5, .9))
                    }
                }
            }
        }
        compose.onNodeWithText(isolateStudyText("قانون Ohm هو [[math:V = IR]]")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(CairoFont, family) }
        capture("cairo-arabic-mixed-science")
    }

    @Test fun predictedRecallIsOptionalAndDistinctFromObservedEvents() {
        var prediction by mutableStateOf(CurrentPredictedRecall(13, 25, .88))
        compose.setContent { RecallTheme(darkTheme = false) { Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RecallSpacing.md)) {
                ObservedRecallSection(history)
                PredictedRecallSection(prediction)
            }
        } } }
        compose.onNodeWithTag("observed-recall-percentage").assertTextEquals("90%")
        compose.onNodeWithTag("predicted-recall").assertDoesNotExist()
        compose.onNodeWithTag("predicted-recall-percentage").assertDoesNotExist()
        compose.onNodeWithTag("predicted-recall-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("predicted-recall-percentage").assertTextEquals("88%")
        compose.onNodeWithTag("predicted-recall-coverage").assertTextEquals(isolateStudyText("Prediction coverage: 13 / 25 active cards"))
        capture("insights-predicted-light")
        compose.runOnIdle { prediction = CurrentPredictedRecall(1, 25, .5) }
        compose.onNodeWithTag("predicted-recall-percentage").assertTextEquals("50%")
        compose.onNodeWithText(isolateStudyText("This estimate covers a small group of cards, not your entire library.")).assertExists()
        compose.onNodeWithTag("predicted-recall-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("predicted-recall").assertDoesNotExist()
        compose.onNodeWithTag("observed-recall-percentage").assertTextEquals("90%")
    }

    @Test fun emptyAndSmallPredictionsExplainCoverageInAllFiveLanguagesIncludingLargeDarkRtl() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var language by mutableStateOf("en")
        var prediction by mutableStateOf(CurrentPredictedRecall(0, 5))
        compose.setContent {
            val context = remember(language) { RecallLocale.context(app, language) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, if (language == "ar") 1.5f else 1f)) {
                RecallTheme(darkTheme = language == "ar", language = language) { Surface {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(RecallSpacing.md)) {
                        PredictedRecallSection(prediction)
                    }
                } }
            }
        }
        compose.onNodeWithTag("predicted-recall-toggle").performClick()
        for (code in RecallLocale.languages) {
            compose.runOnIdle { language = code; prediction = CurrentPredictedRecall(0, 5) }
            val s = RecallStrings(RecallLocale.context(app, code).resources)
            compose.onNodeWithText(s(R.string.insights_predicted_title)).assertExists()
            compose.onNodeWithTag("predicted-recall-percentage").assertDoesNotExist()
            compose.onNodeWithText(isolateStudyText(s(R.string.insights_predicted_empty))).assertExists()
            compose.onNodeWithTag("predicted-recall-coverage").performScrollTo().assertTextEquals(
                isolateStudyText(s(R.string.insights_predicted_coverage, s.number(0), s.number(5))))
            compose.runOnIdle { prediction = CurrentPredictedRecall(1, 5, .9) }
            compose.onNodeWithTag("predicted-recall-percentage").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(isolateStudyText(s(R.string.insights_predicted_small))).assertExists()
            if (code == "ar") capture("insights-predicted-dark-arabic-large-font")
        }
        compose.runOnIdle { prediction = CurrentPredictedRecall() }
        compose.onNodeWithTag("predicted-recall-percentage").assertDoesNotExist()
        val s = RecallStrings(RecallLocale.context(app, language).resources)
        compose.onNodeWithTag("predicted-recall-coverage").assertTextEquals(
            isolateStudyText(s(R.string.insights_predicted_coverage, s.number(0), s.number(0))))
    }

    @Test fun observedEventsMaturityExplanationAndOptionalLessonActionAreAccessible() {
        var opened: String? = null
        val evidence = AttentionEvidence("card", "lesson", 2, 5, period.currentStart)
        val suggestion = AttentionLesson(AttentionGroup("lesson", 3, evidence), AttentionCardDetails("card", "lesson",
            "لماذا يكون Mn²⁺ أكثر استقرارًا؟", "Iron · الحديد", "Chemistry", 7.5, 3, 9.0, 8, period.currentStart))
        compose.setContent { RecallTheme(darkTheme = false) { Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RecallSpacing.md), verticalArrangement = Arrangement.spacedBy(RecallSpacing.md)) {
                ObservedRecallSection(history)
                MemoryMaturitySection(MemoryCounts(12, 2, 3))
                InsightsExplanation(history)
                AttentionLessonRow(suggestion, period.end) { opened = "lesson" }
            }
        } } }
        compose.onNodeWithTag("observed-recall-percentage").assertTextEquals("90%")
        compose.onNodeWithTag("observed-recall-counts").assertTextEquals(isolateStudyText("27 successful / 30 eligible reviews · 10 cards"))
        compose.onNodeWithTag("observed-recall-change").assertExists()
        capture("insights-light")
        compose.onNodeWithTag("insights-explanation-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("insights-explanation").assertExists()
        compose.onNodeWithText(isolateStudyText("Iron · الحديد")).performScrollTo().performClick()
        assertEquals("lesson", opened)
        compose.onNodeWithText("History").performScrollTo().performClick()
        compose.onNodeWithText(isolateStudyText("Next scheduled review: Due or overdue")).assertExists()
    }

    @Test fun emptyAndInsufficientHistoryStayHelpfulInAllFiveLanguagesAndLargeRtl() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var language by mutableStateOf("en")
        var insufficient by mutableStateOf(false)
        var legacy by mutableIntStateOf(0)
        compose.setContent {
            val context = remember(language) { RecallLocale.context(app, language) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, if (language == "ar") 1.5f else 1f)) {
                RecallTheme(darkTheme = language == "ar", language = language) { Surface {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(RecallSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(RecallSpacing.md)) {
                        val available = if (insufficient) RecallSample(7, 9, 4) else RecallSample()
                        val empty = RecallHistoryAnalysis(period, available, missingAuditCurrent = legacy)
                        ObservedRecallSection(empty)
                        MemoryMaturitySection(MemoryCounts())
                        InsightsExplanation(empty)
                    }
                } }
            }
        }
        for (code in RecallLocale.languages) {
            compose.runOnIdle { language = code; insufficient = code != "en" }
            val s = RecallStrings(RecallLocale.context(app, code).resources)
            compose.onNodeWithTag("observed-recall").performScrollTo()
            compose.onNodeWithText(s(R.string.insights_recall_title)).assertExists()
            compose.onNodeWithTag("observed-recall-percentage").assertDoesNotExist()
            compose.onNodeWithTag("observed-recall-change").assertDoesNotExist()
            compose.onNodeWithTag("observed-recall-counts").performScrollTo().assertIsDisplayed()
            val expected = if (code == "en") s(R.string.insights_no_sample) else s(R.string.insights_insufficient)
            compose.onNodeWithText(isolateStudyText(expected)).assertExists()
            if (code == "ar") capture("insights-dark-arabic-large-font")
        }
        compose.runOnIdle { language = "en"; insufficient = false; legacy = 3 }
        val english = RecallStrings(RecallLocale.context(app, "en").resources)
        compose.onNodeWithTag("observed-recall").performScrollTo()
        compose.onNodeWithText(isolateStudyText(english(R.string.insights_legacy_sample))).assertExists()
        compose.onNodeWithTag("observed-recall-percentage").assertDoesNotExist()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "insights-validation").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
