package com.example.myapplication4

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.Rating
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class StabilityRegressionTest {
    @Test fun duplicateRatingCommitsExactlyOnceAndDeletedCardCannotBeRated(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("review", vm) }
        try {
            val dao = db.dao()
            val subject = SubjectEntity(name = "Chemistry")
            val lesson = LessonEntity(subjectId = subject.id, title = "Iron")
            val card = CardEntity(lessonId = lesson.id, front = "Why?", back = "Because.")
            dao.insertSubject(subject); dao.insertLesson(lesson); dao.insertCard(card)
            dao.saveState(ReviewStateEntity(cardId = card.id))
            val queued = dao.lessonCards(lesson.id).single()
            val result = vm.preview(queued, System.currentTimeMillis()).getValue(Rating.GOOD)
            val saved = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            // Same preview submitted twice, even before Compose can disable a button.
            val jobs = listOf(vm.rate(queued, result, 1000) { saved.add(it) }, vm.rate(queued, result, 1000) { saved.add(it) })
            jobs.joinAll()
            assertEquals(listOf(false, true), saved.sorted())
            assertEquals(1, dao.allLogs().size)
            assertEquals(1, dao.reviewState(card.id)!!.reps)
            dao.deleteCard(card.id)
            vm.rate(queued, result, 1000) { saved.add(it) }.join()
            assertEquals(false, saved.last())
            assertTrue(dao.allLogs().isEmpty())
            assertNull(dao.reviewState(card.id))
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun dueQueueIsSubjectScopedAndMatchesDashboardAndReminders(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val visible = SubjectEntity(name = "Physics")
            val other = SubjectEntity(name = "Chemistry")
            val archived = SubjectEntity(name = "Old", archived = true)
            for (subject in listOf(visible, other, archived)) {
                dao.insertSubject(subject)
                for (hidden in listOf(false, true)) {
                    val lesson = LessonEntity(subjectId = subject.id, title = "Lesson", archived = hidden)
                    dao.insertLesson(lesson)
                    for (suspended in listOf(false, true)) {
                        val card = CardEntity(lessonId = lesson.id, front = "Question", back = "Answer", suspended = suspended)
                        dao.insertCard(card); dao.saveState(ReviewStateEntity(cardId = card.id, dueAt = 0))
                    }
                }
            }
            assertEquals(2, dao.dueCards(1).size)
            assertEquals(1, dao.dueCards(1, visible.id).size)
            assertEquals("Physics", dao.dueCards(1, visible.id).single().subjectName)
            assertEquals(2, dao.dueCount(1).first())
            assertEquals(2, dao.reminderDueCount(1))
            assertEquals(2, dao.lessonOverviews(1).first().size)
            assertEquals(2, dao.lessonOverviews(1).first().sumOf { it.due })
            assertTrue(dao.lessonOverviewsForSubject(archived.id, 1).first().isEmpty())
        } finally { db.close() }
    }
}
