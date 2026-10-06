package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SubjectPersistenceTest {
    @Test fun migrationKeepsVersionOneSubjects() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-" + java.util.UUID.randomUUID() + ".db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = org.json.JSONObject(instrumentation.context.assets.open("com.example.myapplication4.data.RecallDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(value: String) = value.replace("$" + "{TABLE_NAME}", entity.getString("tableName"))
                db.execSQL(sql(entity.getString("createSql")))
                val indices = entity.getJSONArray("indices")
                for(j in 0 until indices.length()) db.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
            db.execSQL("INSERT INTO SubjectEntity VALUES ('old', 'Chemistry', 'book', 'blue', 1, 0, 0)")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, RecallDatabase::class.java, name).addMigrations(RecallDatabase.MIGRATION_1_2, RecallDatabase.MIGRATION_2_3, RecallDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Chemistry", db.dao().subjectById("old")!!.name)
            db.dao().insertResource(SubjectResourceEntity(subjectId = "old", title = "After upgrade"))
            assertEquals(1, db.dao().allResources().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun editsKeepCardHistoryAndChapterDeletionKeepsResources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val subject = SubjectEntity(name = "Chemistry")
            dao.insertSubject(subject)
            val chapter = ChapterEntity(subjectId = subject.id, name = "Ions")
            dao.insertChapter(chapter)
            val lesson = LessonEntity(subjectId = subject.id, chapterId = chapter.id, title = "States")
            dao.insertLesson(lesson)
            val card = CardEntity(lessonId = lesson.id, front = "Old question", back = "Answer")
            dao.insertCard(card)
            val state = ReviewStateEntity(cardId = card.id, reps = 7, stability = 20.0)
            dao.saveState(state)
            val resource = SubjectResourceEntity(subjectId = subject.id, title = "Notes", note = "Original")
            dao.insertResource(resource)
            dao.updateSubject(subject.copy(name = "Chemistry II"))
            dao.updateCard(card.copy(front = "Edited question"))
            dao.updateResource(resource.copy(note = "Updated"))
            assertEquals(state, dao.allStates().single())
            assertEquals(card.id, dao.allCards().single().id)
            assertEquals("Edited question", dao.allCards().single().front)
            dao.deleteChapter(chapter.id)
            assertNull(dao.lessonById(lesson.id)!!.chapterId)
            assertEquals("Updated", dao.resources(subject.id).first().single().note)
            dao.deleteSubject(subject.id)
            assertTrue(dao.allResources().isEmpty())
        } finally { db.close() }
    }
}
