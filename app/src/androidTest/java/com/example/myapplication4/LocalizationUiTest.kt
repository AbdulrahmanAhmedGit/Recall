package com.example.myapplication4

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.Rating
import com.example.myapplication4.domain.RecallImportParser
import com.example.myapplication4.domain.ImportResult
import com.example.myapplication4.notifications.ReminderNotifications
import com.example.myapplication4.ui.design.isolateStudyText
import com.example.myapplication4.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fiveLanguagesNavigateReviewAndEditWithoutTranslatingStudyContent(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val original = app.recallPreferences.data.first()
        app.recallPreferences.edit { it[PreferenceKeys.language] = "en"; it[PreferenceKeys.introductionSeen] = true; it[PreferenceKeys.reviewGesturesSeen] = false; it[PreferenceKeys.remindersEnabled] = false }
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("localization", vm) }
        var visible by mutableStateOf(true)
        var enlarged by mutableStateOf(false)
        val subject = SubjectEntity(name = "Chemistry · الكيمياء")
        val chapter = ChapterEntity(subjectId = subject.id, name = "Chapter 2 · الحديد")
        val lesson = LessonEntity(subjectId = subject.id, chapterId = chapter.id, title = "Iron · الحديد")
        val card = CardEntity(lessonId = lesson.id, front = "ما معادلة تكوين الماء؟", back = "[[chem:2H₂ + O₂ → 2H₂O]]\nWater · الماء")
        try {
            db.dao().insertSubject(subject); db.dao().insertChapter(chapter); db.dao().insertLesson(lesson)
            db.dao().insertCard(card); db.dao().saveState(ReviewStateEntity(cardId = card.id))
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if(enlarged) 1.3f else 1f)) {
                    if (visible) RecallApp(vm)
                }
            }
            for (language in listOf("ar", "es", "fr", "de", "en")) {
                vm.setLanguage(language).join()
                vm.setThemeMode(if (language in setOf("ar", "de")) "dark" else "light").join()
                compose.runOnIdle { enlarged = language == "ar" }
                val s = RecallStrings(RecallLocale.context(app, language).resources)
                compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(s(R.string.ui_today)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription(s(R.string.ui_today)).performClick()
                compose.onNodeWithText(s(R.string.ui_start_review)).assertIsDisplayed()
                compose.onNodeWithText(s(R.string.ui_today_counts, s.count(R.plurals.ui_lessons, 1), s.count(R.plurals.ui_minutes, 1))).assertIsDisplayed()
                capture("today-$language")
                compose.onNodeWithText(s(R.string.focus_title)).performClick()
                compose.onNodeWithText(s(R.string.focus_choose_subject)).performClick()
                compose.onNodeWithText(isolateStudyText(subject.name)).performClick()
                compose.onNodeWithText(s(R.string.focus_due)).assertExists()
                compose.onNodeWithText(s(R.string.focus_practice)).assertExists()
                capture("focus-$language")
                pressSystemBack()
                compose.onNodeWithText(s(R.string.ui_start_review)).performClick()
                if (language == "ar") {
                    compose.onNodeWithText(s(R.string.review_gestures_title)).assertIsDisplayed()
                    compose.onNodeWithText(s(R.string.ui_done)).performClick()
                    compose.waitUntil(5_000) { vm.reviewGesturesSeen.value == true }
                } else compose.onNodeWithText(s(R.string.review_gestures_title)).assertDoesNotExist()
                compose.onNodeWithText(s(R.string.ui_show_answer)).assertIsDisplayed().performClick()
                compose.onNodeWithText(s.rating(Rating.GOOD)).assertIsDisplayed()
                compose.onNodeWithText(isolateStudyText(card.back)).assertIsDisplayed()
                capture("review-$language")
                compose.onNodeWithContentDescription(s(R.string.ui_end_review)).performClick()
                compose.onNodeWithContentDescription(s(R.string.ui_library)).performClick()
                compose.onNodeWithText(isolateStudyText(subject.name)).assertIsDisplayed().performClick()
                compose.onAllNodesWithText(isolateStudyText(chapter.name))[0].assertIsDisplayed()
                compose.onNodeWithText(isolateStudyText(lesson.title)).performClick()
                compose.onAllNodesWithText(s(R.string.ui_cards))[0].assertExists()
                capture("lesson-$language")
                compose.onNodeWithText(s(R.string.lesson_pause_title)).performClick()
                compose.onNodeWithText(s(R.string.lesson_pause_today)).assertExists()
                compose.onNodeWithText(s(R.string.lesson_pause_custom)).assertExists()
                capture("lesson-pause-$language")
                pressSystemBack()
                compose.onNodeWithContentDescription(s(R.string.ui_lesson_actions)).performClick()
                compose.onNodeWithText(s(R.string.ui_edit_lesson)).performClick()
                compose.onNodeWithText(s(R.string.ui_lesson_title)).assertExists()
                compose.onNodeWithText(lesson.title).assertExists()
                // Dismiss editor and leave both nested destinations using their actual navigation.
                pressSystemBack()
                compose.onNodeWithContentDescription(s(R.string.ui_back)).performClick()
                compose.onNodeWithContentDescription(s(R.string.ui_back)).performClick()
                compose.onNodeWithContentDescription(s(R.string.ui_insights)).performClick()
                compose.onNodeWithTag("insights-list").performScrollToNode(hasText(s(R.string.activity_title)))
                compose.onNodeWithText(s(R.string.activity_title)).assertIsDisplayed()
                compose.onNodeWithContentDescription(s(R.string.ui_settings)).performClick()
                // Main destinations retain their scroll position between visits.
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.ui_settings_hint)))
                compose.onNodeWithText(s(R.string.ui_settings_hint)).assertIsDisplayed()
                capture("settings-$language")
                // Intercept only the external settings launch. A configuration-context
                // launch without an Activity would fail before reaching this monitor.
                val monitor = instrumentation.addMonitor(android.content.IntentFilter(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS), android.app.Instrumentation.ActivityResult(0, null), true)
                try {
                    compose.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.ui_notification_settings)))
                    compose.onNodeWithText(s(R.string.ui_notification_settings)).performClick()
                    assertEquals(1, monitor.hits)
                } finally { instrumentation.removeMonitor(monitor) }
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.ui_language)))
                compose.onNodeWithText(s(R.string.ui_language)).performClick()
                capture("language-picker-$language")
                val names = linkedMapOf("ar" to "العربية", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "en" to "English")
                for (autonym in names.values) compose.onAllNodesWithText(autonym)[0].assertExists()
                val next = names.keys.toList().let { it[(it.indexOf(language) + 1) % it.size] }
                compose.onNodeWithText(names.getValue(next)).performClick()
                compose.waitUntil(5_000) { vm.settings.value.language == next }
                assertEquals(next, UserPreferences(app).settings.first().language)
                val updated = RecallStrings(RecallLocale.context(app, next).resources)
                compose.onNodeWithContentDescription(updated(R.string.ui_today)).performClick()
            }
            assertEquals(card.front, db.dao().cardsForLesson(lesson.id).first().single().front)
            assertTrue(db.dao().allLogs().isEmpty())
        } finally {
            compose.runOnIdle { visible = false }
            instrumentation.runOnMainSync { store.clear() }
            app.recallPreferences.updateData { original }
        }
    }

    @Test fun localizedResourcesFormatNumbersErrorsIntervalsAndNotifications() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            for (language in RecallLocale.languages) {
                val localized = RecallLocale.context(context, language)
                val s = RecallStrings(localized.resources)
                assertEquals(language, s.locale.language)
                assertEquals(if(language == "ar") android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR, localized.resources.configuration.layoutDirection)
                for (count in listOf(0, 1, 2, 5, 23, 101)) assertTrue(s.count(R.plurals.ui_cards, count).isNotBlank())
                assertEquals(when(language) {
                    "ar" -> "دقيقة واحدة (${s.number(1)})"
                    "es" -> "1 minuto"
                    "fr" -> "1 minute"
                    "de" -> "1 Minute"
                    else -> "1 minute"
                }, s.count(R.plurals.ui_minutes, 1))
                assertTrue(s.date(1_791_200_000_000L, true).isNotBlank())
                assertEquals(s(R.string.ui_week_interval, s.number(2)), s.interval(14 * 86_400_000L))
                assertEquals(s(R.string.ui_second_interval, s.number(1)), s.interval(1000))
                val failure = RecallImportParser.parse("{broken") as ImportResult.Failure
                assertTrue(s.importError(failure.message).isNotBlank())
                if(language != "en") assertNotEquals(failure.message, s.importError(failure.message))
                assertNull(ReminderNotifications.post(localized, 23, test = true))
                // Posting crosses a Binder boundary; wait for Android to replace the prior copy.
                val deadline = System.currentTimeMillis() + 3_000
                while (manager.activeNotifications.firstOrNull { it.id == 1002 }?.notification?.extras?.getString(Notification.EXTRA_TITLE) != s(R.string.ui_test_notification_title) && System.currentTimeMillis() < deadline) Thread.sleep(25)
                val notification = manager.activeNotifications.single { it.id == 1002 }.notification
                assertEquals(s(R.string.ui_test_notification_title), notification.extras.getString(Notification.EXTRA_TITLE))
                assertEquals(s(R.string.ui_review_now), notification.actions[0].title.toString())
                assertEquals(s(R.string.ui_notification_pause), notification.actions[1].title.toString())
            }
        } finally { manager.cancel(1002) }
    }

    // Compose sheets have their own focused window. Send a real Android Back
    // event instead of asking Espresso to focus the obscured activity root.
    private fun pressSystemBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Include modal windows, rather than accidentally capturing the background root.
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val folder = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "localization-validation").apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
