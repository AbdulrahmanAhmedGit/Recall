package com.example.myapplication4

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PronunciationImportFlowTest {
    @Test fun importAppendEditDuplicateAndReimportPreserveTargetsAndMemory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("recall", vm) }
        try {
            val subject = SubjectEntity(name = "German")
            db.dao().insertSubject(subject)
            val raw = """
                {"version":2,"subject":"German","lesson":"Greetings","content_type":"language_learning","learning_language":"de",
                 "cards":[{"type":"qa","front":"Say Guten Morgen","back":"Good morning","pronunciation_targets":[
                 {"side":"front","text":"Guten Morgen","occurrence":1},{"side":"back","text":"Good morning","language":"en"}]}]}
            """.trimIndent()
            val draft = (RecallImportParser.parse(raw) as ImportResult.Success).draft
            vm.importDraft(draft, {}, subject.id).join()
            assertNull(vm.notice.value)
            val dao = db.dao()
            assertEquals(1, dao.allSubjects().size)
            val lesson = dao.allLessons().single()
            assertEquals("de", lesson.learningLanguage)
            val card = dao.allCards().single()
            assertEquals(2, dao.targetsForCard(card.id).size)
            val state = dao.allStates().single().copy(reps = 8, stability = 22.0, dueAt = 90000)
            dao.saveState(state)
            vm.importDraft(draft, {}, subject.id, lesson.id).join()
            assertEquals(1, dao.allCards().size)
            assertEquals(2, dao.allPronunciationTargets().size)
            vm.editCard(card.copy(back = "A morning greeting"), listOf(PronunciationTarget("front", "Guten Morgen"))).join()
            assertEquals(1, dao.targetsForCard(card.id).size)
            assertEquals(state, dao.allStates().single())
            vm.duplicateCard(dao.allCards().single()).join()
            assertEquals(2, dao.allCards().size)
            assertEquals(2, dao.allPronunciationTargets().size)
            assertEquals(state, dao.allStates().single { it.cardId == card.id })
            val exported = RecallBackupCodec.encode(dao.backup(), UserSettings(speechRate = 1.2f))
            val restored = RecallBackupCodec.decode(exported) as BackupResult.Success
            assertEquals(2, restored.data.pronunciationTargets.size)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
