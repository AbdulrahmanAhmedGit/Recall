package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LearningInsightsPersistenceTest {
    @Test fun stateFlowUpdatesImmediatelyAfterARealAnswerAndSuspension() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val dao = db.dao()
        dao.insertSubject(SubjectEntity(id = "s", name = "Physics"))
        dao.insertLesson(LessonEntity(id = "l", subjectId = "s", title = "Current"))
        dao.insertCard(CardEntity(id = "c", lessonId = "l", front = "What is current?", back = "Charge per unit time."))
        val old = ReviewStateEntity(cardId = "c", state = "review", dueAt = System.currentTimeMillis() - 1000,
            lastReviewedAt = System.currentTimeMillis() - 2 * 86_400_000, stability = 8.0, reps = 3, scheduledDays = 2)
        dao.saveState(old)
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("insights", vm) }
        try {
            val initial = withTimeout(5000) { vm.learningInsights.first { it != null }!! }
            assertEquals(0, initial.history.current.total)
            assertEquals(1, initial.predicted.eligibleCards)
            assertEquals(1, initial.predicted.activeCards)
            val card = dao.lessonCards("l").single()
            val result = vm.preview(card, System.currentTimeMillis()).getValue(Rating.GOOD)
            vm.rate(card, result, 1200).join()
            val updated = withTimeout(5000) { vm.learningInsights.first { it?.history?.current?.total == 1 }!! }
            assertEquals(RecallSample(1, 1, 1), updated.history.current)
            val predicted = withTimeout(5000) { vm.learningInsights.first { it?.predicted?.probability == 1.0 }!! }
            assertEquals(1, predicted.predicted.eligibleCards)
            assertEquals(result.state, dao.allStates().single())
            val storedLog = dao.allLogs().single()
            dao.setSuspended("c", true, System.currentTimeMillis())
            val suspended = withTimeout(5000) { vm.learningInsights.first { it?.memory?.total == 0 && it.history.current.total == 0 }!! }
            assertEquals(MemoryCounts(), suspended.memory)
            val excluded = withTimeout(5000) { vm.learningInsights.first { it?.predicted?.activeCards == 0 }!! }
            assertEquals(CurrentPredictedRecall(), excluded.predicted)
            assertEquals(storedLog, dao.allLogs().single())
            assertEquals(result.state, dao.allStates().single())
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun predictionProjectionFiltersActiveContentAndKeepsUnknownStatesInCoverage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val now = System.currentTimeMillis()
            dao.insertSubject(SubjectEntity(id = "s", name = "Chemistry"))
            dao.insertSubject(SubjectEntity(id = "archived", name = "Old", archived = true))
            dao.insertLesson(LessonEntity(id = "l", subjectId = "s", title = "Iron"))
            dao.insertLesson(LessonEntity(id = "old", subjectId = "s", title = "Old lesson", archived = true))
            dao.insertLesson(LessonEntity(id = "old-subject", subjectId = "archived", title = "Old subject lesson"))
            suspend fun card(id: String, lesson: String = "l", state: String = "review", stability: Double = 21.0,
                suspended: Boolean = false, at: Long? = now - 7 * 86_400_000) {
                dao.insertCard(CardEntity(id = id, lessonId = lesson, front = "Why?", back = "Because", suspended = suspended))
                dao.saveState(ReviewStateEntity(cardId = id, state = state, stability = stability, lastReviewedAt = at, reps = 3))
            }
            card("eligible")
            card("learning", state = "learning"); card("relearning", state = "relearning")
            card("new", state = "new"); card("unknown", at = null)
            card("infinite", stability = Double.POSITIVE_INFINITY); card("zero", stability = 0.0)
            card("future", at = now + 60_000)
            card("suspended", suspended = true); card("archived-lesson", lesson = "old")
            card("archived-subject", lesson = "old-subject")
            dao.insertCard(CardEntity(id = "missing-state", lessonId = "l", front = "Missing", back = "State"))
            val before = dao.allStates()
            val rows = dao.insightsPredictionCards().first()
            assertEquals(9, rows.size)
            assertNull(rows.single { it.cardId == "missing-state" }.stability)
            val predictor = CurrentRecallPredictor()
            val result = predictor.calculate(rows, now)
            assertEquals(1, result.eligibleCards)
            assertEquals(9, result.activeCards)
            assertEquals(Fsrs6MemoryModel().forgettingCurve(7.0, 21.0), result.probability!!, 0.0)
            assertEquals(before, dao.allStates())
            assertTrue(dao.allLogs().isEmpty())
            dao.setSuspended("eligible", true, now)
            val after = predictor.calculate(dao.insightsPredictionCards().first(), now)
            assertEquals(8, after.activeCards)
            assertNull(after.probability)
            assertEquals(4, db.openHelper.readableDatabase.version)
        } finally { db.close() }
    }

    @Test fun delayedSubmissionPersistsTheFrozenPreviewTimestampAndSchedule() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val dao = db.dao()
        val zone = ZoneId.of("Africa/Cairo")
        val today = LocalDate.now(zone)
        val presented = today.minusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        dao.insertSubject(SubjectEntity(id = "s", name = "Chemistry"))
        dao.insertLesson(LessonEntity(id = "l", subjectId = "s", title = "Iron"))
        dao.insertCard(CardEntity(id = "c", lessonId = "l", front = "Why?", back = "Because"))
        dao.saveState(ReviewStateEntity(cardId = "c", state = "review", reps = 3, stability = 10.0,
            lastReviewedAt = presented - 2 * 86_400_000, scheduledDays = 2, dueAt = presented - 1000))
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("frozen", vm) }
        try {
            val card = dao.lessonCards("l").single()
            val result = vm.preview(card, presented).getValue(Rating.GOOD)
            vm.rate(card, result, System.currentTimeMillis() - presented).join()
            val log = dao.allLogs().single()
            assertEquals(presented, log.reviewedAt)
            assertEquals(today.minusDays(1), java.time.Instant.ofEpochMilli(log.reviewedAt).atZone(zone).toLocalDate())
            assertEquals(result.state, dao.allStates().single())
            assertEquals(result.state.dueAt, log.nextDueAt)
            val analysis = analyzeRecallHistory(dao.insightsReviews(InsightsPeriod(today, zone).historyStart,
                InsightsPeriod(today, zone).end).first(), InsightsPeriod(today, zone), System.currentTimeMillis())
            assertEquals(1, analysis.current.total)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun activeFilteringCategoryCountsAndHistoricalEligibilityUsePersistedData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.insertSubject(SubjectEntity(id = "s", name = "Chemistry"))
            dao.insertSubject(SubjectEntity(id = "archived", name = "Old", archived = true))
            dao.insertLesson(LessonEntity(id = "l", subjectId = "s", title = "Iron"))
            dao.insertLesson(LessonEntity(id = "old", subjectId = "s", title = "Old lesson", archived = true))
            dao.insertLesson(LessonEntity(id = "old-subject", subjectId = "archived", title = "Old subject lesson"))
            suspend fun card(id: String, lesson: String = "l", reps: Int = 0, state: String = "new", stability: Double = .4, suspended: Boolean = false) {
                dao.insertCard(CardEntity(id = id, lessonId = lesson, front = "لماذا يكون Mn²⁺ مستقرًا؟", back = "[[chem:[Ar] 3d⁵]]", suspended = suspended))
                dao.saveState(ReviewStateEntity(cardId = id, reps = reps, state = state, stability = stability))
            }
            card("new"); card("first-again", reps = 1, state = "learning")
            card("young", reps = 3, state = "review", stability = 20.99)
            card("mature", reps = 5, state = "review", stability = 21.0)
            card("relearning", reps = 9, state = "relearning", stability = 80.0)
            card("invalid-stability", reps = 5, state = "review", stability = Double.POSITIVE_INFINITY)
            card("suspended", reps = 5, state = "review", stability = 40.0, suspended = true)
            card("archived-lesson", "old", 4, "review", 40.0)
            card("archived-subject", "old-subject", 4, "review", 40.0)
            // Missing state is counted conservatively as New, without writing a replacement state.
            dao.insertCard(CardEntity(id = "missing-state", lessonId = "l", front = "Why?", back = "Because"))
            assertEquals(MemoryCounts(7, 2, 1), dao.insightsMemoryCounts(21.0, Double.MAX_VALUE).first())
            val period = InsightsPeriod(LocalDate.of(2026, 10, 8), ZoneId.of("Africa/Cairo"))
            val at = period.today.atTime(12, 0).atZone(period.zone).toInstant().toEpochMilli()
            fun log(cardId: String, day: Long = 0, rating: Int = 3, audit: Boolean = true) = ReviewLogEntity(
                cardId = cardId, reviewedAt = at - day * 86_400_000, rating = rating,
                previousInterval = 2, nextInterval = 4, previousStability = 2.0, newStability = 4.0, durationMillis = 2000,
                previousDueAt = if (audit) at - day * 86_400_000 - 1000 else 0,
                previousState = if (audit) "review" else "new", reps = if (audit) 4 else 0, elapsedDays = if (audit) 2.0 else 0.0)
            dao.addLog(log("young", 0, 1)); dao.addLog(log("young", 1, 3)); dao.addLog(log("young", 2, 1))
            dao.addLog(log("mature", audit = false))
            dao.addLog(log("suspended")); dao.addLog(log("archived-lesson")); dao.addLog(log("archived-subject"))
            dao.addLog(log("deleted-card")) // A legacy orphan must not become invented current knowledge.
            dao.addLog(log("young", 90))
            val beforeStates = dao.allStates()
            val events = dao.insightsReviews(period.historyStart, period.end).first()
            assertEquals(4, events.size)
            val insights = analyzeRecallHistory(events, period, at + 1)
            assertEquals(RecallSample(1, 3, 1), insights.current)
            assertEquals(1, insights.missingAuditCurrent)
            val selected = dao.insightsAttentionCards(insights.attention.map { it.example.cardId }).first().single()
            assertEquals("young", selected.cardId)
            assertEquals("Iron", selected.lessonTitle)
            assertEquals(beforeStates, dao.allStates())
            dao.setSuspended("young", true, at)
            assertEquals(1, dao.insightsReviews(period.historyStart, period.end).first().size)
            assertEquals(MemoryCounts(6, 2, 1), dao.insightsMemoryCounts(21.0, Double.MAX_VALUE).first())
            assertTrue(dao.insightsAttentionCards(listOf("young")).first().isEmpty())
            dao.setSuspended("young", false, at)
            dao.deleteCard("young")
            assertEquals(1, dao.insightsReviews(period.historyStart, period.end).first().size)
            assertEquals(4, db.openHelper.readableDatabase.version)
            val plan = db.openHelper.readableDatabase.query("""EXPLAIN QUERY PLAN SELECT r.id FROM ReviewLogEntity r
                JOIN CardEntity c ON c.id = r.cardId JOIN LessonEntity l ON l.id = c.lessonId
                JOIN SubjectEntity s ON s.id = l.subjectId WHERE r.reviewedAt >= ? AND r.reviewedAt < ?
                AND c.suspended = 0 AND l.archived = 0 AND s.archived = 0""", arrayOf(period.historyStart, period.end)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString(" ")
            }
            assertTrue(plan, plan.contains("index_ReviewLogEntity_reviewedAt"))
        } finally { db.close() }
    }
}
