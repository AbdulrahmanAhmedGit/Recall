package com.example.myapplication4

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReviewCalendarPersistenceTest {
    @Test fun dueCountsRefreshWhenReturningAfterADueTimePasses(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("clock", vm) }
        try {
            val subject = SubjectEntity(name = "Physics")
            val lesson = LessonEntity(subjectId = subject.id, title = "Current")
            val card = CardEntity(lessonId = lesson.id, front = "What is current?", back = "Charge flow per unit time.")
            db.dao().insertSubject(subject); db.dao().insertLesson(lesson); db.dao().insertCard(card)
            val dueAt = System.currentTimeMillis() + 600
            db.dao().saveState(ReviewStateEntity(cardId = card.id, dueAt = dueAt))
            assertEquals(0, db.dao().dueCount(System.currentTimeMillis()).first())
            delay(800)
            vm.onResume().join()
            assertEquals(1, withTimeout(5000) { vm.dueCount.first { it == 1 } })
            assertEquals(dueAt, db.dao().allStates().single().dueAt)
            assertTrue(db.dao().allLogs().isEmpty())
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }

    @Test fun realDueStatesAreGroupedAndArchivedOrSuspendedCardsAreExcluded() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val subject = SubjectEntity(name = "Chemistry")
            val lesson = LessonEntity(subjectId = subject.id, title = "Iron")
            dao.insertSubject(subject); dao.insertLesson(lesson)
            val zone = ZoneId.of("Asia/Kolkata")
            val today = LocalDate.of(2026, 12, 31)
            fun at(date: LocalDate, hour: Int = 0, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            suspend fun card(id: String, date: LocalDate, type: String = "qa", suspended: Boolean = false, lessonId: String = lesson.id, reps: Int = 1) {
                dao.insertCard(CardEntity(id = id, lessonId = lessonId, type = type, front = "Question $id", back = "Answer", suspended = suspended))
                dao.saveState(ReviewStateEntity(cardId = id, dueAt = at(date, 0, 15), reps = reps))
            }
            card("overdue", today.minusDays(4))
            card("today", today, "cloze", reps = 0)
            card("tomorrow", today.plusDays(1))
            card("suspended", today, suspended = true)
            val archived = lesson.copy(id = "archived", archived = true)
            dao.insertLesson(archived); card("hidden-lesson", today, lessonId = archived.id)
            val hiddenSubject = subject.copy(id = "hidden-subject", name = "Archived chemistry", archived = true)
            dao.insertSubject(hiddenSubject)
            dao.insertLesson(lesson.copy(id = "hidden-subject-lesson", subjectId = hiddenSubject.id))
            card("hidden-subject-card", today, lessonId = "hidden-subject-lesson")
            val period = ReviewCalendarPeriod(YearMonth.from(today), today, zone)
            val day = dao.reviewCalendarCounts(reviewCalendarQuery(period)).first().single()
            assertEquals(ReviewCalendarDay(today.toEpochDay(), 2, 1, 1, 1, 1), day)
            val (start, end) = calendarDayBounds(today, today, zone)
            assertEquals(listOf("overdue", "today"), dao.reviewCalendarCards(start, end, 100).first().map { it.id })
            val next = dao.reviewCalendarCounts(reviewCalendarQuery(period.copy(month = period.month.plusMonths(1)))).first().single()
            assertEquals(today.plusDays(1), next.date)
            assertEquals(1, next.total)
            // Completed logs are not forecasts; only current state dueAt controls the calendar.
            dao.addLog(ReviewLogEntity(cardId = "overdue", reviewedAt = at(today), rating = 3, previousInterval = 0, nextInterval = 1, previousStability = .4, newStability = 1.0, durationMillis = 1000))
            assertEquals(day, dao.reviewCalendarCounts(reviewCalendarQuery(period)).first().single())
            dao.saveState(ReviewStateEntity(cardId = "overdue", dueAt = at(today.plusDays(3))))
            assertEquals(1, dao.reviewCalendarCounts(reviewCalendarQuery(period)).first().single().total)
            dao.deleteCard("today")
            assertTrue(dao.reviewCalendarCounts(reviewCalendarQuery(period)).first().isEmpty())
        } finally { db.close() }
    }

    @Test fun dstBoundariesAndLargeQueueRemainExactAndDetailsAreBounded() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val subject = SubjectEntity(name = "Physics")
            val lesson = LessonEntity(subjectId = subject.id, title = "Current")
            val zone = ZoneId.of("America/New_York")
            val today = LocalDate.of(2026, 3, 1)
            val dst = LocalDate.of(2026, 3, 8)
            db.withTransaction {
                dao.insertSubject(subject); dao.insertLesson(lesson)
                repeat(600) { index ->
                    dao.insertCard(CardEntity(id = "card-$index", lessonId = lesson.id, front = "Question $index", back = "Answer"))
                    val at = if (index == 0) dst.atTime(23, 59) else dst.atTime(0, 15)
                    dao.saveState(ReviewStateEntity(cardId = "card-$index", dueAt = at.atZone(zone).toInstant().toEpochMilli()))
                }
                dao.insertCard(CardEntity(id = "boundary", lessonId = lesson.id, front = "Next day", back = "Answer"))
                dao.saveState(ReviewStateEntity(cardId = "boundary", dueAt = dst.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()))
            }
            val rows = dao.reviewCalendarCounts(reviewCalendarQuery(ReviewCalendarPeriod(YearMonth.from(today), today, zone))).first()
            assertEquals(600, rows.single { it.date == dst }.total)
            assertEquals(1, rows.single { it.date == dst.plusDays(1) }.total)
            val (start, end) = calendarDayBounds(dst, today, zone)
            val firstPage = dao.reviewCalendarCards(start, end, 100).first()
            assertEquals(100, firstPage.size)
            assertEquals(firstPage, dao.reviewCalendarCards(start, end, 200).first().take(100))
            assertEquals(600, dao.reviewCalendarCards(start, end, 700).first().size)
            val query = reviewCalendarQuery(ReviewCalendarPeriod(YearMonth.from(today), today, zone))
            val explanation = object : androidx.sqlite.db.SupportSQLiteQuery {
                override val sql = "EXPLAIN QUERY PLAN " + query.sql
                override val argCount = query.argCount
                override fun bindTo(statement: androidx.sqlite.db.SupportSQLiteProgram) = query.bindTo(statement)
            }
            val plan = db.openHelper.readableDatabase.query(explanation).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString(" ")
            }
            assertTrue(plan.contains("index_ReviewStateEntity_dueAt"))
        } finally { db.close() }
    }
}
