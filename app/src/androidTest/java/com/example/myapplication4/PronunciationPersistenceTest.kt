package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PronunciationPersistenceTest {
    @Test fun versionTwoMigrationPreservesCardsMemoryAndResources() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "pronunciation-migration-" + UUID.randomUUID() + ".db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = org.json.JSONObject(instrumentation.context.assets.open("com.example.myapplication4.data.RecallDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(value: String) = value.replace("$" + "{TABLE_NAME}", entity.getString("tableName"))
                old.execSQL(sql(entity.getString("createSql")))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
            old.execSQL("INSERT INTO SubjectEntity VALUES ('s', 'German', 'book', 'blue', 1, 0, 0)")
            old.execSQL("INSERT INTO LessonEntity VALUES ('l', 's', NULL, 'Greetings', NULL, NULL, 1, 1, 0)")
            old.execSQL("INSERT INTO CardEntity VALUES ('c', 'l', 'qa', 'Guten Morgen', 'Good morning', NULL, NULL, 1, 1, 0)")
            old.execSQL("INSERT INTO ReviewStateEntity VALUES ('c', 'review', 9000, 1000, 20.0, 4.0, 10, 7, 2)")
            old.execSQL("INSERT INTO SubjectResourceEntity VALUES ('r', 's', 'Notes', 'note', 'Keep me', NULL, NULL, 1, 1)")
            old.version = 2
        }
        var db = Room.databaseBuilder(context, RecallDatabase::class.java, name).addMigrations(RecallDatabase.MIGRATION_2_3, RecallDatabase.MIGRATION_3_4).build()
        try {
            val dao = db.dao()
            assertEquals(7, dao.allStates().single().reps)
            assertEquals(20.0, dao.allStates().single().stability, 0.0)
            assertEquals("Keep me", dao.allResources().single().note)
            assertEquals("general", dao.lessonById("l")!!.contentType)
            assertNull(dao.lessonById("l")!!.learningLanguage)
            assertTrue(dao.allPronunciationTargets().isEmpty())
            dao.updateLesson(dao.lessonById("l")!!.copy(contentType = "language_learning", learningLanguage = "de"))
            val target = PronunciationTarget("front", "Guten Morgen").entity("c")
            dao.insertPronunciationTargets(listOf(target))
            db.close()
            db = Room.databaseBuilder(context, RecallDatabase::class.java, name).build()
            assertEquals(target, db.dao().targetsForCard("c").single())
            assertEquals("de", db.dao().lessonById("l")!!.learningLanguage)
            db.dao().deleteCard("c")
            assertTrue(db.dao().allPronunciationTargets().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun replaceAndBackupMergeKeepReviewStateAndNeverDuplicateTargets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val subject = SubjectEntity(name = "English")
            val lesson = LessonEntity(subjectId = subject.id, title = "Words", learningLanguage = "en")
            val card = CardEntity(lessonId = lesson.id, front = "instructions directions", back = "Compare them")
            dao.insertSubject(subject); dao.insertLesson(lesson); dao.insertCard(card)
            val state = ReviewStateEntity(cardId = card.id, reps = 12, dueAt = 12345)
            dao.saveState(state)
            dao.replacePronunciationTargets(card.id, listOf(PronunciationTarget("front", "instructions").entity(card.id)))
            val changed = PronunciationTarget("front", "directions", "en-GB").entity(card.id)
            dao.replacePronunciationTargets(card.id, listOf(changed))
            assertEquals(listOf(changed), dao.targetsForCard(card.id))
            assertEquals(state, dao.allStates().single())
            val backup = dao.backup()
            dao.mergeBackup(backup)
            assertEquals(1, dao.allPronunciationTargets().size)
            assertEquals(state, dao.allStates().single())
            dao.deleteSubject(subject.id)
            assertTrue(dao.allPronunciationTargets().isEmpty())
            dao.mergeBackup(backup)
            assertEquals(listOf(changed), dao.allPronunciationTargets())
        } finally { db.close() }
    }
}
