package com.example.myapplication4

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.CardWithLesson
import com.example.myapplication4.ui.ReviewCardStack
import com.example.myapplication4.ui.design.RecallTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReviewStackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun skippingBeforeAndAfterRevealDoesNotRecordReviews() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.example.myapplication4.data.RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = androidx.lifecycle.ViewModelStore().apply { put("skip", vm) }
        try {
            val subject = com.example.myapplication4.data.SubjectEntity(name = "Chemistry")
            val lesson = com.example.myapplication4.data.LessonEntity(subjectId = subject.id, title = "Iron")
            db.dao().insertSubject(subject)
            db.dao().insertLesson(lesson)
            repeat(3) { index ->
                val card = com.example.myapplication4.data.CardEntity(lessonId = lesson.id, front = "Question $index", back = "Answer $index", createdAt = index.toLong())
                db.dao().insertCard(card)
                db.dao().saveState(com.example.myapplication4.data.ReviewStateEntity(cardId = card.id))
            }
            val cards = db.dao().lessonCards(lesson.id)
            val initial = db.dao().allStates().associateBy { it.cardId }
            val restoration = StateRestorationTester(compose)
            restoration.setContent { RecallTheme { Surface { com.example.myapplication4.ui.ReviewScreen(cards, vm) {} } } }
            compose.onNodeWithText("Skip for now").assertIsDisplayed().performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("2 of 3").assertIsDisplayed()
            compose.onNodeWithText("Show answer").performClick()
            compose.onNodeWithText("Skip for now").assertIsDisplayed().performClick()
            compose.onNodeWithText("3 of 3").assertIsDisplayed()
            compose.onNodeWithText("Answer").assertDoesNotExist()
            org.junit.Assert.assertEquals(initial, db.dao().allStates().associateBy { it.cardId })
            org.junit.Assert.assertTrue(db.dao().allLogs().isEmpty())
            compose.onNodeWithText("Show answer").performClick()
            compose.onNodeWithText("Good").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("1 card reviewed").assertIsDisplayed()
            compose.onNodeWithText("2 skipped · still available for review").assertIsDisplayed()
            compose.waitUntil(5000) { kotlinx.coroutines.runBlocking { db.dao().allLogs().size == 1 } }
            val final = db.dao().allStates().associateBy { it.cardId }
            cards.take(2).forEach { org.junit.Assert.assertEquals(initial[it.id], final[it.id]) }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }

    @Test fun realReviewStackRatesOneCardAndKeepsNextCardUnrevealed() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.example.myapplication4.data.RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = androidx.lifecycle.ViewModelStore().apply { put("review", vm) }
        val subject = com.example.myapplication4.data.SubjectEntity(name = "Chemistry")
        val lesson = com.example.myapplication4.data.LessonEntity(subjectId = subject.id, title = "Oxidation States")
        db.dao().insertSubject(subject); db.dao().insertLesson(lesson)
        repeat(3) { index ->
            val card = com.example.myapplication4.data.CardEntity(
                lessonId = lesson.id,
                front = "ما معادلة اختزال الهيماتيت بالغاز المائي في فرن مدركس؟",
                back = "[[chem:Fe₂O₃(s) + 3H₂(g) → 2Fe(s) + 3H₂O(g)]]\nعند درجة أعلى من 700 °C",
                createdAt = index.toLong(),
            )
            db.dao().insertCard(card); db.dao().saveState(com.example.myapplication4.data.ReviewStateEntity(cardId = card.id))
        }
        val cards = db.dao().lessonCards(lesson.id)
        compose.setContent { RecallTheme(darkTheme = true) { Surface { com.example.myapplication4.ui.ReviewScreen(cards, vm) {} } } }
        try {
            compose.onNodeWithText("Show answer").assertIsDisplayed()
            capture("review-stack-question")
            compose.onNodeWithText("Show answer").performClick()
            compose.onNodeWithText("Good").assertIsDisplayed()
            compose.onNodeWithText("[[chem:", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Fe₂O₃(s) + 3H₂(g) → 2Fe(s) + 3H₂O(g)", substring = true).assertExists()
            capture("review-stack-answer")
            compose.onNodeWithText("Good").performClick()
            compose.onNodeWithText("Show answer").assertIsDisplayed()
            compose.onNodeWithText("Answer").assertDoesNotExist()
            compose.waitUntil(5000) { kotlinx.coroutines.runBlocking { db.dao().allLogs().size == 1 } }
            org.junit.Assert.assertEquals(1, db.dao().allStates().sumOf { it.reps })
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun layeredCardsRevealMixedTextAndAdvanceInLightAndDark() {
        var dark by mutableStateOf(false)
        var revealed by mutableStateOf(false)
        var next by mutableStateOf(false)
        val first = CardWithLesson("1", "lesson", "qa", "لماذا يكون Mn²⁺ أكثر استقرارًا؟", "لأن توزيعه الإلكتروني هو [Ar] 3d⁵ — a half-filled d subshell.", null, null, false, "Oxidation States", "Chemistry", "new", 0, null, 0.0, 5.0, 0, 0, 0)
        compose.setContent {
            RecallTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides if (dark) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                            Text("Oxidation States", style = MaterialTheme.typography.titleMedium)
                            Text(if (next) "2 of 3" else "1 of 3", style = MaterialTheme.typography.labelMedium)
                            ReviewCardStack(if (next) first.copy(id = "2", front = "طبق قانون V = IR") else first, emptyList(), if (next) 2 else 3, revealed, Modifier.weight(1f))
                            Button({ revealed = !revealed }, Modifier.fillMaxWidth()) { Text("Show answer") }
                            Button({ next = true; revealed = false }, Modifier.fillMaxWidth()) { Text("Next card") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Show answer").assertIsDisplayed().performClick()
        compose.onNodeWithText("Answer").assertIsDisplayed()
        capture("stack-light-answer")
        compose.runOnIdle { dark = true }
        compose.waitForIdle()
        compose.onNodeWithText("Answer").assertIsDisplayed()
        capture("stack-dark-rtl")
        compose.onNodeWithText("Next card").performClick()
        compose.onNodeWithText("2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Answer").assertDoesNotExist()
        capture("stack-next")
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "redesign-validation").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
