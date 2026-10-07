package com.example.myapplication4

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.ReviewCalendarMonth
import com.example.myapplication4.ui.ReviewCalendarScreen
import com.example.myapplication4.ui.design.RecallTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*
import java.util.Locale

class ReviewCalendarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun monthNavigationSelectionAndDarkArabicLargeFonts() {
        val today = LocalDate.of(2026, 10, 7)
        var month by mutableStateOf(YearMonth.from(today))
        var selected by mutableStateOf(today)
        var arabic by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            RecallTheme(darkTheme = arabic) {
                CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(density.density, if (arabic) 1.5f else 1f)) {
                    Surface { Column(Modifier.width(320.dp).statusBarsPadding()) {
                        ReviewCalendarMonth(month, today, mapOf(today to ReviewCalendarDay(today.toEpochDay(), 23, 20, 3, 2, 1)),
                            selected, if (arabic) Locale.forLanguageTag("ar-EG") else Locale.UK,
                            { month = it }, { selected = it })
                    } }
                }
            }
        }
        compose.onNodeWithTag("calendar-previous").assertIsNotEnabled()
        compose.onNodeWithTag("calendar-day-$today").assertIsSelected()
        compose.onNodeWithTag("calendar-day-${today.minusDays(1)}").assertIsNotEnabled()
        compose.onNodeWithTag("calendar-day-${today.plusDays(1)}").performClick().assertIsSelected()
        assertEquals(today.plusDays(1), selected)
        capture("calendar-light-small")
        compose.onNodeWithTag("calendar-next").performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithTag("calendar-previous").assertIsEnabled().performClick()
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.runOnIdle { arabic = true }
        compose.onNodeWithText("أكتوبر ٢٠٢٦").assertExists()
        compose.onNodeWithTag("calendar-day-$today").assertExists()
        capture("calendar-dark-arabic-large")
    }

    @Test fun dayListsRealCardsPreviewIsReadOnlyAndReschedulingUpdatesTheCalendar(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("calendar", vm) }
        try {
            val subject = SubjectEntity(name = "Chemistry")
            val lesson = LessonEntity(subjectId = subject.id, title = "Oxidation States")
            val clock = ActivityPeriod.current()
            val tomorrow = clock.today.plusDays(1)
            val card = CardEntity(lessonId = lesson.id, front = "لماذا يكون Mn²⁺ أكثر استقرارًا؟", back = "[[chem:[Ar] 3d⁵]]", type = "cloze")
            val due = clock.today.atTime(8, 0).atZone(clock.zone).toInstant().toEpochMilli()
            db.dao().insertSubject(subject); db.dao().insertLesson(lesson)
            db.dao().insertCard(card); db.dao().saveState(ReviewStateEntity(cardId = card.id, dueAt = due))
            compose.setContent { RecallTheme(darkTheme = false) { ReviewCalendarScreen(vm, {}, {}) } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("1 card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("calendar-card-${card.id}"))
            compose.onNodeWithTag("calendar-card-${card.id}").performScrollTo().performClick()
            compose.onNodeWithTag("calendar-card-details").assertIsDisplayed()
            compose.onNodeWithText("[Ar] 3d⁵", substring = true).assertExists()
            capture("calendar-card-details", "calendar-card-details")
            compose.onNodeWithText("Done").performScrollTo().performClick()
            assertTrue(db.dao().allLogs().isEmpty())
            assertEquals(due, db.dao().allStates().single().dueAt)
            db.dao().saveState(ReviewStateEntity(cardId = card.id, dueAt = tomorrow.atTime(8, 0).atZone(clock.zone).toInstant().toEpochMilli()))
            compose.waitUntil(5000) { compose.onAllNodesWithText("No cards scheduled for this day.").fetchSemanticsNodes().isNotEmpty() }
            if (YearMonth.from(tomorrow) != YearMonth.from(clock.today)) compose.onNodeWithTag("calendar-next").performScrollTo().performClick()
            compose.onNodeWithTag("calendar-day-$tomorrow").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("1 card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("calendar-card-${card.id}"))
            compose.onNodeWithTag("calendar-card-${card.id}").performScrollTo().assertIsDisplayed()
            capture("calendar-scheduled-cards")
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }

    @Test fun mainNavigationOpensCalendarAndTabTransitionsKeepWorking(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("navigation", vm) }
        try {
            vm.completeIntroduction().join()
            vm.setLanguage("en").join()
            compose.setContent { RecallTheme(darkTheme = false) { com.example.myapplication4.ui.RecallRoot(vm) } }
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Review calendar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Review calendar").performClick()
            compose.onNodeWithText("Review calendar").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Library").performClick()
            compose.onNodeWithText("Create your first subject").assertIsDisplayed()
            compose.onNodeWithContentDescription("Insights").performClick()
            compose.onNodeWithContentDescription("Review calendar").performClick()
            compose.onNodeWithText("Review calendar").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Today").performClick()
            compose.onNodeWithText("You’re caught up").assertIsDisplayed()
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }

    private fun capture(name: String, tag: String? = null) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "calendar-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
