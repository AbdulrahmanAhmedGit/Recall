package com.example.myapplication4

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FullBackupDeviceTest {
    @Test fun streamingJsonFileRoundTripPreservesStateHistoryAndNotes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val original = app.recallPreferences.data.first()
        val source = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val sourceVm = RecallViewModel(app, source)
        val targetVm = RecallViewModel(app, destination)
        val store = androidx.lifecycle.ViewModelStore().apply { put("json-source", sourceVm); put("json-destination", targetVm) }
        val file = File.createTempFile("phase3-json", ".json", app.cacheDir)
        try {
            UserPreferences(app).setRemindersEnabled(false)
            source.dao().insertSubject(SubjectEntity(id = "s", name = "Chemistry"))
            source.dao().insertLesson(LessonEntity(id = "l", subjectId = "s", title = "Iron"))
            source.dao().insertCard(CardEntity(id = "c", lessonId = "l", front = "Why?", back = "[[chem:Fe³⁺]]"))
            source.dao().saveState(ReviewStateEntity("c", state = "review", dueAt = 12345, stability = 25.0, reps = 10))
            source.dao().insertResource(SubjectResourceEntity(subjectId = "s", title = "Notes", note = "قانون [[math:V = IR]]"))
            repeat(1000) { i -> source.dao().addLog(ReviewLogEntity(cardId = "c", reviewedAt = i.toLong(), rating = 3, previousInterval = 1, nextInterval = 2, previousStability = 20.0, newStability = 25.0, durationMillis = 100)) }
            var result = ""
            sourceVm.exportBackupFile(Uri.fromFile(file)) { result = it }.join()
            assertEquals(app.getString(R.string.ui_backup_exported), result)
            targetVm.importBackupFile(Uri.fromFile(file)) { result = it }.join()
            assertEquals(app.getString(R.string.ui_backup_restored), result)
            assertEquals(source.dao().backup(), destination.dao().backup())
            targetVm.importBackupFile(Uri.fromFile(file)) { result = it }.join()
            assertEquals(1000, destination.dao().allLogs().size)
        } finally { file.delete(); app.recallPreferences.updateData { original }; instrumentation.runOnMainSync { store.clear() } }
    }
    @Test fun exportAndRestoreCopiesRealMaterialsToOpenablePrivateProvider() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val original = app.recallPreferences.data.first()
        val sourceDb = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val destinationDb = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val sourceVm = RecallViewModel(app, sourceDb)
        val destinationVm = RecallViewModel(app, destinationDb)
        val store = androidx.lifecycle.ViewModelStore().apply { put("source", sourceVm); put("destination", destinationVm) }
        val material = File.createTempFile("attachment", ".pdf", app.cacheDir)
        val archive = File.createTempFile("all-data", ".zip", app.cacheDir)
        var restoredFile: File? = null
        try {
            UserPreferences(app).setRemindersEnabled(false)
            val bytes = "%PDF-1.4\nStudy material — Mn²⁺ العناصر الانتقالية".toByteArray()
            material.writeBytes(bytes)
            val subject = SubjectEntity(name = "Chemistry")
            sourceDb.dao().insertSubject(subject)
            sourceDb.dao().insertResource(SubjectResourceEntity(subjectId = subject.id, title = "Study PDF", kind = "pdf", uri = Uri.fromFile(material).toString(), mimeType = "application/pdf"))
            sourceDb.dao().insertResource(SubjectResourceEntity(subjectId = subject.id, title = "Notes", note = "  قانون V = IR\n"))
            sourceVm.exportAllData(Uri.fromFile(archive)).join()
            assertTrue(sourceVm.archiveStatus.value!!, sourceVm.archiveStatus.value!!.startsWith("All data exported"))
            material.delete() // Restoration must not depend on the source file still existing.
            destinationVm.restoreAllData(Uri.fromFile(archive)).join()
            assertTrue(destinationVm.archiveStatus.value!!, destinationVm.archiveStatus.value!!.startsWith("Full backup restored"))
            val restored = destinationDb.dao().allResources()
            val uri = Uri.parse(restored.single { it.uri != null }.uri)
            assertEquals("content", uri.scheme)
            assertEquals(app.packageName + ".materials", uri.authority)
            app.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(bytes, it.readBytes()) }
            assertEquals("  قانون V = IR\n", restored.single { it.uri == null }.note)
            restoredFile = File(app.filesDir, uri.path!!.removePrefix("/materials/" ).let { "materials/$it" })
            // A second restore merges rather than duplicating or overwriting existing resources.
            destinationVm.restoreAllData(Uri.fromFile(archive)).join()
            assertEquals(2, destinationDb.dao().allResources().size)
        } finally {
            material.delete(); archive.delete()
            restoredFile?.let { it.delete(); it.parentFile?.delete() }
            app.recallPreferences.updateData { original }
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
