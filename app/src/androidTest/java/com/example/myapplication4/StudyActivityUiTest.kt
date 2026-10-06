package com.example.myapplication4

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.StudyActivityCard
import com.example.myapplication4.ui.design.RecallTheme
import com.example.myapplication4.ui.InsightsScreen
import com.example.myapplication4.data.*
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class StudyActivityUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 6)
    private val period = ActivityPeriod(today, ZoneId.of("Africa/Cairo"))

    @Test fun insightsUpdatesFromRealCompletedResponses() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val subject = SubjectEntity(name = "Physics")
        val lesson = LessonEntity(subjectId = subject.id, title = "Current")
        val card = CardEntity(lessonId = lesson.id, front = "What does current measure?", back = "Charge per unit time.")
        db.dao().insertSubject(subject); db.dao().insertLesson(lesson)
        db.dao().insertCard(card); db.dao().saveState(ReviewStateEntity(cardId = card.id))
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("activity", vm) }
        try {
            compose.setContent { RecallTheme(darkTheme = false) { InsightsScreen(vm, emptyList()) } }
            compose.onNodeWithText("Your activity will appear here as you review cards.").assertExists()
            val reviewCard = db.dao().lessonCards(lesson.id).single()
            val reviewedAt = System.currentTimeMillis()
            val result = vm.preview(reviewCard, reviewedAt).getValue(Rating.GOOD)
            compose.runOnIdle { vm.rate(reviewCard, result, 1500) }
            compose.waitUntil(5000) { vm.studyActivity.value.totalReviews == 1 }
            val date = ActivityPeriod.current().today
            compose.onNodeWithTag("activity-day-$date").assertIsDisplayed().performClick()
            compose.onNodeWithText("1 review completed").assertIsDisplayed()
            compose.onNodeWithText("Done").performClick()
            capture("activity-insights-live-data")
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun latestWeekIsVisibleDaysOpenDetailsAndPastCanBeExplored() {
        val activity = StudyActivity(period, (0L..110L).filter { it % 5 != 0L }
            .associate { today.minusDays(it) to listOf(2, 9, 23, 38)[(it % 4).toInt()] } + (today to 23))
        compose.setContent { RecallTheme(darkTheme = false) {
            Surface { Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                StudyActivityCard(activity, locale = Locale.UK)
            } }
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("activity-day-$today").assertIsDisplayed()
        compose.onNodeWithTag("activity-day-${today.plusDays(1)}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tuesday, 6 October 2026, 23 reviews completed").assertExists()
        capture("activity-light-history")
        compose.onNodeWithTag("activity-day-$today").performClick()
        compose.onNodeWithText("23 reviews completed").assertIsDisplayed()
        capture("activity-day-details")
        compose.onNodeWithText("Done").performClick()
        val before = scrollPosition()
        compose.onNodeWithTag("activity-timeline").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(scrollPosition() < before)
        capture("activity-older-months")
    }

    @Test fun emptySmallPhoneHasZeroDetailsAndDarkArabicSupportsLargeFonts() {
        var arabic by mutableStateOf(false)
        compose.setContent { RecallTheme(darkTheme = arabic) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, if (arabic) 1.5f else 1f)) {
                Surface { Column(Modifier.width(320.dp).statusBarsPadding().padding(16.dp)) {
                    val counts = if (arabic) (1L..90L).associate { today.minusDays(it) to listOf(2, 9, 23, 38)[(it % 4).toInt()] } else emptyMap()
                    StudyActivityCard(StudyActivity(period, counts), locale = if (arabic) Locale.forLanguageTag("ar-EG") else Locale.UK)
                } }
            }
        } }
        compose.onNodeWithText("Your activity will appear here as you review cards.").assertIsDisplayed()
        compose.onNodeWithTag("activity-day-$today").assertIsDisplayed().performClick()
        compose.onNodeWithText("No reviews").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        capture("activity-empty-small")
        compose.runOnIdle { arabic = true }
        compose.waitForIdle()
        compose.onNodeWithText("نشاط الدراسة").assertIsDisplayed()
        compose.onNodeWithText("سبت").assertIsDisplayed()
        compose.onNodeWithText("أرب").assertIsDisplayed()
        compose.onNodeWithTag("activity-day-$today").assertIsDisplayed()
        compose.onNodeWithTag("activity-day-${today.plusDays(1)}").assertDoesNotExist()
        capture("activity-dark-arabic-large-font")
        compose.onNodeWithTag("activity-day-$today").performClick()
        compose.onNodeWithText("لا توجد مراجعات").assertIsDisplayed()
        compose.onNodeWithText("تم").performClick()
    }

    private fun scrollPosition() = compose.onNodeWithTag("activity-timeline").fetchSemanticsNode()
        .config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "activity-validation").apply { mkdirs() }
        val node = if (name == "activity-day-details") compose.onNodeWithTag("activity-day-details")
            else compose.onAllNodes(isRoot()).onLast()
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
