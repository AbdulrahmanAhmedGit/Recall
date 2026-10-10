package com.example.myapplication4

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import com.example.myapplication4.util.*
import com.example.myapplication4.ui.design.isolateStudyText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewFocusDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scopedQueuesAndPauseExclusionsDoNotMutateMemory(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        try {
            val dao = db.dao(); val now = System.currentTimeMillis()
            val subject = SubjectEntity(name = "Focus")
            val chapter = ChapterEntity(subjectId = subject.id, name = "Chapter")
            val nested = LessonEntity(subjectId = subject.id, chapterId = chapter.id, title = "Nested")
            val direct = LessonEntity(subjectId = subject.id, title = "Direct")
            val archived = LessonEntity(subjectId = subject.id, title = "Archived", archived = true)
            dao.insertSubject(subject); dao.insertChapter(chapter)
            listOf(nested, direct, archived).forEach { dao.insertLesson(it) }
            val cards = listOf(CardEntity(lessonId = nested.id, front = "Due", back = "Answer"),
                CardEntity(lessonId = nested.id, front = "Future", back = "Answer"),
                CardEntity(lessonId = direct.id, front = "Direct", back = "Answer"),
                CardEntity(lessonId = nested.id, front = "Suspended", back = "Answer", suspended = true),
                CardEntity(lessonId = archived.id, front = "Archived", back = "Answer"))
            cards.forEachIndexed { i, card -> dao.insertCard(card); dao.saveState(ReviewStateEntity(cardId = card.id, dueAt = if(i == 1) now + 86_400_000 else now - 1)) }
            val before = dao.allStates()
            assertEquals(setOf(cards[0].id, cards[2].id), dao.dueCards(now).map { it.id }.toSet())
            assertEquals(listOf(cards[2].id), dao.dueCards(now, excludedLessonIds = listOf(nested.id)).map { it.id })
            assertEquals(1, dao.dueCount(now, listOf(nested.id)).first())
            assertEquals(1, dao.reminderDueCount(now, listOf(nested.id)))
            assertEquals(listOf(cards[2].id), dao.dueBatch(now, 20, 20, excludedLessonIds = listOf(nested.id)).map { it.id })
            assertEquals(listOf(cards[0].id), dao.focusCards(subject.id, chapter.id, null, now, false).map { it.id })
            assertEquals(setOf(cards[0].id, cards[1].id), dao.focusCards(subject.id, null, nested.id, now, true).map { it.id }.toSet())
            assertEquals(listOf(cards[2].id), dao.focusCards(subject.id, null, direct.id, now, false).map { it.id })
            assertEquals(before, dao.allStates()); assertTrue(dao.allLogs().isEmpty())
            dao.saveState(before.first { it.cardId == cards[2].id }.copy(reps = 1))
            assertEquals(listOf(cards[2].id), dao.focusCards(subject.id, null, null, now, false, newLimit = 0).map { it.id })
            assertEquals(3, dao.focusCards(subject.id, null, null, now, true, newLimit = 0).size)
        } finally { db.close() }
    }

    @Test fun pausePersistsAndPracticeRatingsLeaveNoLogsOrScheduleChanges(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val original = app.recallPreferences.data.first()
        app.recallPreferences.edit { it[PreferenceKeys.language] = "en"; it[PreferenceKeys.introductionSeen] = true; it[PreferenceKeys.remindersEnabled] = false; it.remove(PreferenceKeys.lessonPauses); it.remove(PreferenceKeys.reviewPauses) }
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("focus", vm) }
        try {
            val subject = SubjectEntity(name = "Focus science")
            val lesson = LessonEntity(subjectId = subject.id, title = "Pause and practice")
            val card = CardEntity(lessonId = lesson.id, front = "Recall this rule", back = "A useful rule")
            db.dao().insertSubject(subject); db.dao().insertLesson(lesson); db.dao().insertCard(card)
            db.dao().saveState(ReviewStateEntity(cardId = card.id, dueAt = System.currentTimeMillis() - 1))
            val before = db.dao().allStates()
            val pause = LessonReviewPause(lesson.id, 0, System.currentTimeMillis() + 86_400_000)
            vm.setLessonPause(lesson.id, pause).join()
            assertEquals(listOf(pause), UserPreferences(app).settings.first().lessonPauses)
            val s = RecallStrings(RecallLocale.context(app, "en").resources)
            compose.setContent { RecallApp(vm) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(s(R.string.focus_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(isolateStudyText(lesson.title)))
            compose.onNodeWithText(isolateStudyText(lesson.title)).performClick()
            compose.onNodeWithText(s(R.string.lesson_pause_resume)).performClick()
            compose.waitUntil(5_000) { runBlocking { UserPreferences(app).settings.first().lessonPauses.isEmpty() } }
            // Reinstate the pause: explicitly focused practice may still include it.
            vm.setLessonPause(lesson.id, pause).join()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.focus_title)))
            compose.onNodeWithText(s(R.string.focus_title)).performClick()
            compose.onNodeWithText(s(R.string.focus_choose_subject)).performClick()
            compose.onNodeWithText(subject.name).performClick()
            compose.onNodeWithText(s(R.string.focus_practice)).performClick()
            compose.onNodeWithText(s(R.string.focus_start)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.ui_show_answer)).assertIsDisplayed().performClick()
            compose.onNodeWithText(s.rating(Rating.GOOD)).performClick()
            compose.onNodeWithText(s(R.string.ui_done)).assertExists()
            assertEquals(before, db.dao().allStates()); assertTrue(db.dao().allLogs().isEmpty())
            // Due focus opts back into the unchanged normal scheduling path.
            compose.onNodeWithText(s(R.string.ui_done)).performClick()
            compose.onNodeWithText(s(R.string.focus_title)).performClick()
            compose.onNodeWithText(s(R.string.focus_choose_subject)).performClick()
            compose.onNodeWithText(subject.name).performClick()
            compose.onNodeWithText(s(R.string.focus_start)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.ui_show_answer)).performClick()
            compose.onNodeWithText(s.rating(Rating.GOOD)).performClick()
            compose.waitUntil(5_000) { runBlocking { db.dao().allLogs().size == 1 } }
            assertEquals(1, db.dao().allStates().single().reps)
            assertTrue(db.dao().allStates().single().dueAt > before.single().dueAt)
            vm.setLessonPause(lesson.id, null).join()
            assertTrue(UserPreferences(app).settings.first().lessonPauses.isEmpty())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            app.recallPreferences.updateData { original }
        }
    }
}
