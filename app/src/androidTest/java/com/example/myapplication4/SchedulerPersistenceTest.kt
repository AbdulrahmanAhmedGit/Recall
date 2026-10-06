package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.RecallDatabase
import com.example.myapplication4.data.CardEntity
import com.example.myapplication4.data.LessonEntity
import com.example.myapplication4.data.ReviewLogEntity
import com.example.myapplication4.data.ReviewStateEntity
import com.example.myapplication4.data.SubjectEntity
import com.example.myapplication4.domain.FsrsScheduler
import com.example.myapplication4.domain.Rating
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SchedulerPersistenceTest {
    @Test fun versionThreeMigrationPreservesHistoryAndAddsFsrsAuditFields() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "scheduler-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = org.json.JSONObject(
            instrumentation.context.assets.open("com.example.myapplication4.data.RecallDatabase/3.json")
                .bufferedReader().use { it.readText() },
        ).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(value: String) = value.replace("${'$'}{TABLE_NAME}", entity.getString("tableName"))
                old.execSQL(sql(entity.getString("createSql")))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
            old.execSQL("INSERT INTO ReviewLogEntity VALUES ('log', 'card', 1000, 3, 2, 8, 2.3, 8.1, 9000)")
            old.version = 3
        }

        var db = Room.databaseBuilder(context, RecallDatabase::class.java, name)
            .addMigrations(RecallDatabase.MIGRATION_3_4).build()
        try {
            var log = db.dao().allLogs().single()
            assertEquals("log", log.id)
            assertEquals(8.1, log.newStability, 0.0)
            assertEquals(0L, log.previousDueAt)
            assertEquals("new", log.previousState)
            db.close()

            db = Room.databaseBuilder(context, RecallDatabase::class.java, name).build()
            log = db.dao().allLogs().single()
            assertEquals(8, log.nextInterval)
            assertTrue(log.newDifficulty in 1.0..10.0)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun committedReviewStateAndHistorySurviveDatabaseRestart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "scheduler-restart-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, RecallDatabase::class.java, name).build()
        try {
            val subject = SubjectEntity(id = "s", name = "Chemistry")
            val lesson = LessonEntity(id = "l", subjectId = subject.id, title = "Iron")
            val card = CardEntity(id = "c", lessonId = lesson.id, front = "Why?", back = "Because.")
            val old = ReviewStateEntity(cardId = card.id, dueAt = 10_000L)
            db.dao().insertSubject(subject); db.dao().insertLesson(lesson); db.dao().insertCard(card); db.dao().saveState(old)
            val result = FsrsScheduler().schedule(old, Rating.GOOD, 20_000L)
            val log = ReviewLogEntity(
                cardId = card.id, reviewedAt = result.reviewedAt, rating = result.rating.value,
                previousInterval = result.previousDays, nextInterval = result.nextDays,
                previousStability = old.stability, newStability = result.state.stability,
                durationMillis = 5000, previousDueAt = result.previousDueAt,
                nextDueAt = result.state.dueAt, elapsedDays = result.elapsedDays,
                previousDifficulty = old.difficulty, newDifficulty = result.state.difficulty,
                previousState = result.previousState, newState = result.state.state,
                reps = result.state.reps, lapses = result.state.lapses,
            )
            db.dao().commitReview(result.state, log)
            db.close()

            db = Room.databaseBuilder(context, RecallDatabase::class.java, name).build()
            assertEquals(result.state, db.dao().allStates().single())
            assertEquals(result.state.dueAt, db.dao().allLogs().single().nextDueAt)
            assertEquals(2, db.dao().allLogs().single().nextInterval)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
