package com.example.myapplication4

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.util.*
import com.example.myapplication4.ui.design.isolateStudyText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Public documentation captures: isolated synthetic data, never a personal library. */
class ReadmeScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureCurrentNativeScreensWithDemoData(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val original = app.recallPreferences.data.first()
        app.recallPreferences.edit {
            it[PreferenceKeys.language] = "en"
            it[PreferenceKeys.introductionSeen] = true
            it[PreferenceKeys.remindersEnabled] = false
        }
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("readme", vm) }
        var visible by mutableStateOf(true)
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val chemistry = SubjectEntity(id = "demo-chemistry", name = "Chemistry", accent = "purple")
        val physics = SubjectEntity(id = "demo-physics", name = "Physics", accent = "blue")
        val german = SubjectEntity(id = "demo-german", name = "German", accent = "teal")
        val iron = LessonEntity(id = "demo-iron", subjectId = chemistry.id, title = "Iron & oxidation states", summary = "Understand iron's common oxidation states and why half-filled electron configurations are stable.")
        try {
            val dao = db.dao()
            listOf(chemistry, physics, german).forEach { dao.insertSubject(it) }
            listOf(iron, LessonEntity(id = "demo-current", subjectId = physics.id, title = "Electric current"),
                LessonEntity(id = "demo-greetings", subjectId = german.id, title = "Everyday expressions", contentType = "language_learning", learningLanguage = "de")).forEach { dao.insertLesson(it) }
            repeat(18) { i ->
                val lesson = listOf(iron.id, "demo-current", "demo-greetings")[i % 3]
                val front = when (i % 3) { 0 -> "Why is Mn²⁺ relatively stable?"; 1 -> "What relationship does Ohm's law express?"; else -> "What does ‘auf Wiedersehen’ mean?" }
                val back = when (i % 3) { 0 -> "Its [[chem:[Ar] 3d⁵]] configuration has a half-filled d subshell."; 1 -> "[[math:V = IR]]\nVoltage equals current multiplied by resistance."; else -> "Goodbye — a polite way to say farewell." }
                dao.insertCard(CardEntity(id = "demo-card-$i", lessonId = lesson, front = front, back = back))
                dao.saveState(ReviewStateEntity(cardId = "demo-card-$i", state = "review", dueAt = now + (i - 8) * day,
                    lastReviewedAt = now - 7 * day, stability = 12.0 + i, reps = 8, scheduledDays = 7))
            }
            for (daysAgo in 1..100) {
                if (daysAgo % 5 == 0) continue
                repeat(listOf(3, 9, 18, 32)[daysAgo % 4]) { event ->
                    val at = now - daysAgo * day
                    dao.addLog(ReviewLogEntity(id = "demo-log-$daysAgo-$event", cardId = "demo-card-${event % 18}", reviewedAt = at,
                        rating = if (event % 11 == 0) 1 else 3, previousInterval = 7, nextInterval = 12,
                        previousStability = 12.0, newStability = 20.0, durationMillis = 8_000,
                        previousDueAt = at - day, nextDueAt = at + 12 * day, elapsedDays = 7.0,
                        previousState = "review", newState = "review", reps = 8))
                }
            }
            dao.insertResource(SubjectResourceEntity(subjectId = chemistry.id, title = "Iron: key ideas", note = "Compare Fe²⁺ and Fe³⁺. Keep reaction conditions alongside each equation."))
            dao.insertResource(SubjectResourceEntity(subjectId = chemistry.id, title = "Revision checklist", note = "Oxidation states • extraction • alloy types • common misconceptions"))
            vm.setLanguage("en").join(); vm.setThemeMode("light").join()
            compose.setContent { if (visible) RecallApp(vm) }
            val s = RecallStrings(RecallLocale.context(app, "en").resources)
            compose.onNodeWithContentDescription(s(R.string.ui_library)).performClick()
            compose.waitUntil(10_000) { vm.subjects.value.size == 3 }
            compose.onNodeWithText(isolateStudyText("Chemistry")).assertIsDisplayed()
            capture("library")
            compose.onNodeWithText(isolateStudyText("Chemistry")).performClick()
            compose.onNodeWithText(s(R.string.ui_resources)).performClick()
            compose.onNodeWithText(isolateStudyText("Iron: key ideas")).assertIsDisplayed()
            capture("resources")
            compose.onNodeWithContentDescription(s(R.string.ui_back)).performClick()
            vm.setThemeMode("dark").join()
            compose.onNodeWithContentDescription(s(R.string.ui_today)).performClick()
            compose.onNodeWithText(s(R.string.ui_start_review)).performClick()
            compose.onNodeWithText(s(R.string.ui_show_answer)).assertIsDisplayed().performClick()
            capture("review")
            compose.onNodeWithContentDescription(s(R.string.ui_end_review)).performClick()
            dao.updateCard(CardEntity(id = "demo-card-0", lessonId = iron.id,
                front = "لماذا يكون Mn²⁺ أكثر استقرارًا؟",
                back = "لأن توزيعه الإلكتروني [[chem:[Ar] 3d⁵]] يحتوي على مستوى d نصف ممتلئ."))
            vm.setLanguage("ar").join()
            val ar = RecallStrings(RecallLocale.context(app, "ar").resources)
            compose.onNodeWithText(ar(R.string.ui_start_review)).performClick()
            compose.onNodeWithText(ar(R.string.ui_show_answer)).assertIsDisplayed().performClick()
            capture("review-arabic")
            compose.onNodeWithContentDescription(ar(R.string.ui_end_review)).performClick()
            vm.setLanguage("en").join(); vm.setThemeMode("light").join()
            compose.onNodeWithContentDescription(s(R.string.ui_insights)).performClick()
            compose.waitUntil(10_000) { vm.learningInsights.value != null && vm.studyActivity.value.totalReviews > 0 }
            compose.onNodeWithTag("study-activity").assertIsDisplayed()
            capture("insights")
            compose.onNodeWithContentDescription(s(R.string.calendar_title)).performClick()
            compose.onNodeWithText(s(R.string.calendar_title)).assertIsDisplayed()
            capture("calendar")
        } finally {
            compose.runOnIdle { visible = false }
            instrumentation.runOnMainSync { store.clear() }
            app.recallPreferences.updateData { original }
            db.close()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // UiAutomation reads the display, not the semantics tree. Let the compositor
        // finish the short navigation/reveal transition before taking its pixels.
        Thread.sleep(800)
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "readme-preview4").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
