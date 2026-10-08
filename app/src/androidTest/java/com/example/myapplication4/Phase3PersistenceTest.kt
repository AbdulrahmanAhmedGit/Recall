package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class Phase3PersistenceTest {
    @Test fun boundedSessionsKeepDueDatesExcludeInactiveAndHonorNewCardCap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val subject = SubjectEntity(id = "s", name = "Science")
            val lesson = LessonEntity(id = "l", subjectId = "s", title = "Lesson")
            db.dao().insertSubject(subject); db.dao().insertLesson(lesson)
            repeat(250) { i ->
                val id = "c%04d".format(i)
                db.dao().insertCard(CardEntity(id = id, lessonId = "l", front = "Q$i", back = "A$i", suspended = i == 249))
                db.dao().saveState(ReviewStateEntity(id, dueAt = i.toLong(), reps = if (i < 30) 0 else 5, state = if (i < 30) "new" else "review"))
            }
            val before = db.dao().allStates()
            val batch = db.dao().dueBatch(1000, 2, 20)
            assertEquals(20, batch.size); assertEquals(2, batch.count { it.reps == 0 })
            assertEquals(listOf("c0000", "c0001", "c0030"), batch.take(3).map { it.id })
            assertEquals(batch, db.dao().dueBatch(1000, 2, 20))
            val deferred = db.dao().dueBatch(1000, 2, 20, batch.map { it.id })
            assertTrue(deferred.none { card -> card.id in batch.map { it.id } })
            assertEquals(before, db.dao().allStates()); assertTrue(db.dao().allLogs().isEmpty())
            assertEquals(249, db.dao().dueCount(1000).first())
            assertEquals(0, db.dao().dueBatch(-1, 20, 20).size)
            assertEquals(1, db.dao().dueBatch(0, 20, 20).size)
            assertEquals(10, db.dao().dueBatch(1000, 20, 10).size)
            assertEquals(40, db.dao().dueBatch(1000, 20, 40).size)
            assertTrue(db.dao().dueBatch(1000, 0, 20).all { it.reps > 0 })
            db.dao().updateSubject(subject.copy(archived = true))
            assertTrue(db.dao().dueBatch(1000, 20, 20).isEmpty())
        } finally { db.close() }
    }
    @Test fun staleEditsSuspensionAndDuplicateRatingCannotCommit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.insertSubject(SubjectEntity(id = "s", name = "Science")); dao.insertLesson(LessonEntity(id = "l", subjectId = "s", title = "Lesson"))
            val card = CardEntity(id = "c", lessonId = "l", front = "Why?", back = "Because")
            val old = ReviewStateEntity("c", dueAt = 0)
            dao.insertCard(card); dao.saveState(old)
            val shown = dao.dueCards(100).single()
            val result = FsrsScheduler().schedule(old, Rating.GOOD, 100)
            val log = ReviewLogEntity(cardId = "c", reviewedAt = 100, rating = 3, previousInterval = 0, nextInterval = 2, previousStability = old.stability, newStability = result.state.stability, durationMillis = 5)
            dao.updateCard(card.copy(front = "Changed"))
            assertFalse(dao.commitReviewIfCurrent(old, result.state, log, shown))
            dao.updateCard(card.copy(suspended = true))
            assertFalse(dao.commitReviewIfCurrent(old, result.state, log, shown))
            dao.updateCard(card)
            dao.updateSubject(SubjectEntity(id = "s", name = "Science", archived = true))
            assertFalse(dao.commitReviewIfCurrent(old, result.state, log, shown))
            dao.updateSubject(SubjectEntity(id = "s", name = "Science"))
            assertTrue(dao.commitReviewIfCurrent(old, result.state, log, shown))
            assertFalse(dao.commitReviewIfCurrent(old, result.state, log, shown))
            assertEquals(1, dao.allLogs().size)
            dao.deleteCard("c")
            assertFalse(dao.commitReviewIfCurrent(result.state, result.state, log, shown))
        } finally { db.close() }
    }
    @Test fun restoredExistingCardsKeepStateAndHistoryTogetherAndConflictsRollback() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val data = BackupData(listOf(SubjectEntity(id = "s", name = "Science")), emptyList(), listOf(LessonEntity(id = "l", subjectId = "s", title = "Lesson")),
                listOf(CardEntity(id = "c", lessonId = "l", front = "Q", back = "A")), emptyList(), emptyList(), listOf(ReviewStateEntity("c", dueAt = 123)), emptyList(), emptyList(), emptyList())
            dao.mergeBackup(data)
            val log = ReviewLogEntity(cardId = "c", reviewedAt = 10, rating = 3, previousInterval = 0, nextInterval = 1, previousStability = .4, newStability = 1.0, durationMillis = 5)
            dao.mergeBackup(data.copy(states = listOf(ReviewStateEntity("c", dueAt = 999)), logs = listOf(log)))
            assertEquals(123L, dao.reviewState("c")!!.dueAt); assertTrue(dao.allLogs().isEmpty())
            dao.addLog(log)
            val before = dao.backup()
            try { dao.mergeBackup(data.copy(subjects = listOf(SubjectEntity(id = "other", name = "Science")), lessons = listOf(data.lessons.single().copy(subjectId = "other")))); fail("Expected conflict") } catch (_: IllegalArgumentException) { }
            assertEquals(before, dao.backup())
            val conflicting = data.copy(cards = listOf(data.cards.single().copy(id = "c2")), states = listOf(ReviewStateEntity("c2")), logs = listOf(log.copy(cardId = "c2")))
            try { dao.mergeBackup(conflicting); fail("Expected log-ID conflict") } catch (_: IllegalArgumentException) { }
            assertEquals(before, dao.backup())
        } finally { db.close() }
    }
    @Test fun futureLogsAreExcludedAndMidnightBelongsToLocalCompletionDay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val now = Instant.parse("2026-10-08T22:15:00Z").toEpochMilli()
            val zone = ZoneId.of("Africa/Cairo")
            val period = ActivityPeriod(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), zone)
            for (at in listOf(now - 7_200_000, now, now + 60_000)) db.dao().addLog(ReviewLogEntity(cardId = "historical", reviewedAt = at, rating = 3, previousInterval = 1, nextInterval = 2, previousStability = 1.0, newStability = 2.0, durationMillis = 1))
            val rows = db.dao().studyActivity(studyActivityQuery(period, now)).first()
            assertEquals(2, rows.sumOf { it.reviewCount })
            assertEquals(1, rows.single { it.epochDay == period.today.toEpochDay() }.reviewCount)
            assertEquals(1, db.dao().reviewCountSince(period.today.atStartOfDay(zone).toInstant().toEpochMilli(), now).first())
        } finally { db.close() }
    }
}
