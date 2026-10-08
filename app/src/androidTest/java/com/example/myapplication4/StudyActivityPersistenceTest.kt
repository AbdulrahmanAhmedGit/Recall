package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StudyActivityPersistenceTest {
    private fun log(at: Instant) = ReviewLogEntity(cardId = "card", reviewedAt = at.toEpochMilli(), rating = 3,
        previousInterval = 0, nextInterval = 2, previousStability = .4, newStability = 2.0, durationMillis = 1000)

    @Test fun completedResponsesAggregateLocallyAndDueCardsDoNotCount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val subject = SubjectEntity(id = "subject", name = "Physics")
            val lesson = LessonEntity(id = "lesson", subjectId = subject.id, title = "Current")
            dao.insertSubject(subject); dao.insertLesson(lesson)
            dao.insertCard(CardEntity(id = "card", lessonId = lesson.id, front = "Question", back = "Answer"))
            dao.saveState(ReviewStateEntity(cardId = "card", dueAt = 0))
            val zone = ZoneId.of("Asia/Kolkata")
            val today = LocalDate.of(2026, 1, 1)
            val period = ActivityPeriod(today, zone)
            assertTrue(dao.studyActivity(studyActivityQuery(period)).first().isEmpty())
            val midnight = today.atTime(0, 15).atZone(zone).toInstant()
            dao.commitReview(ReviewStateEntity(cardId = "card", dueAt = period.endMillis + 1), log(midnight))
            dao.addLog(log(midnight.plusSeconds(60))) // Two responses, same card/day, both count.
            dao.addLog(log(today.minusDays(1).atTime(23, 45).atZone(zone).toInstant()))
            dao.addLog(log(today.plusDays(1).atTime(0, 15).atZone(zone).toInstant()))
            dao.addLog(log(period.firstDate.minusDays(1).atStartOfDay(zone).toInstant()))
            val counts = dao.studyActivity(studyActivityQuery(period, period.endMillis - 1)).first().associate { LocalDate.ofEpochDay(it.epochDay) to it.reviewCount }
            assertEquals(mapOf(today.minusDays(1) to 1, today to 2), counts)
        } finally { db.close() }
    }

    @Test fun historicalOffsetsAreUsedAcrossDstAndTheDateRange() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val zone = ZoneId.of("America/New_York")
            val period = ActivityPeriod(LocalDate.of(2026, 11, 2), zone)
            val dates = listOf(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))
            dates.forEach { db.dao().addLog(log(it.atTime(0, 15).atZone(zone).toInstant())) }
            db.dao().addLog(log(LocalDateTime.of(2026, 3, 8, 3, 15).atZone(zone).toInstant()))
            // Both occurrences of the repeated hour must remain on the same local day.
            listOf(ZoneOffset.ofHours(-4), ZoneOffset.ofHours(-5)).forEach { offset ->
                db.dao().addLog(log(LocalDateTime.of(2026, 11, 1, 1, 30).toInstant(offset)))
            }
            db.dao().addLog(log(period.today.atTime(0, 15).atZone(zone).toInstant()))
            val counts = db.dao().studyActivity(studyActivityQuery(period, period.endMillis - 1)).first().associate { LocalDate.ofEpochDay(it.epochDay) to it.reviewCount }
            assertEquals(1, counts[dates[0]])
            assertEquals(1, counts[dates[1]])
            assertEquals(2, counts[dates[2]])
            assertEquals(3, counts[dates[3]])
            assertEquals(1, counts[period.today])
            val plan = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN " + studyActivityQuery(period).sql,
                studyActivityQuery(period).bindArgsForTest()).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString(" ")
            }
            assertTrue(plan.contains("index_ReviewLogEntity_reviewedAt"))
        } finally { db.close() }
    }
}

private fun androidx.sqlite.db.SimpleSQLiteQuery.bindArgsForTest(): Array<Any?> {
    val args = arrayOfNulls<Any>(argCount)
    bindTo(object : androidx.sqlite.db.SupportSQLiteProgram {
        override fun bindNull(index: Int) { args[index - 1] = null }
        override fun bindLong(index: Int, value: Long) { args[index - 1] = value }
        override fun bindDouble(index: Int, value: Double) { args[index - 1] = value }
        override fun bindString(index: Int, value: String) { args[index - 1] = value }
        override fun bindBlob(index: Int, value: ByteArray) { args[index - 1] = value }
        override fun clearBindings() { args.fill(null) }
        override fun close() {}
    })
    return args
}
