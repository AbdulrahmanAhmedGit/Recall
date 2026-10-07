package com.example.myapplication4

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.*
import com.example.myapplication4.ui.design.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PronunciationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "pronunciation-validation").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun tapWord(text: String, word: String) {
        // Room metadata loads asynchronously after the question itself is first visible.
        try { compose.waitUntil(15000) {
            compose.onAllNodes(hasText(isolateStudyText(text)), useUnmergedTree = true).fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.isNotEmpty()
            }
        } } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw error
        }
        val node = compose.onNode(hasText(isolateStudyText(text)), useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val offset = isolateStudyText(text).indexOf(word)
        val rect = results.single().getBoundingBox(offset + word.length / 2)
        node.performTouchInput { click(rect.center) }
    }

    @Test fun mixedTextLinksHaveExactHitAreasAndWorkInLightDarkAndLargeRtl() {
        val spoken = mutableListOf<Pair<String, String>>()
        val sentence = "الفرق بين \"instructions\" و \"directions\" هو..."
        val targets = listOf(PronunciationTarget("front", "instructions", "en-GB"), PronunciationTarget("front", "directions", "en"))
        var dark by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            RecallTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalPronunciation provides { word, language -> spoken += word to language },
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(LocalDensity.current.density, scale),
                ) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.widthIn(max = 320.dp).padding(RecallSpacing.ml), verticalArrangement = Arrangement.spacedBy(RecallSpacing.lg)) {
                            Text("QUESTION", style = MaterialTheme.typography.labelSmall)
                            PronounceableStudyText(sentence, "front", targets, style = MaterialTheme.typography.headlineMedium)
                            PronounceableStudyText("كيف تنطق \"Entschuldigung\" بالألمانية؟", "front", listOf(PronunciationTarget("front", "Entschuldigung", "de")))
                            PronounceableStudyText("You should take care of your belongings.", "back", listOf(PronunciationTarget("back", "take care of", "en")))
                            BidiAwareText("توزيع Mn²⁺ هو [Ar] 3d⁵")
                        }
                    }
                }
            }
        }
        tapWord(sentence, "instructions")
        tapWord(sentence, "directions")
        compose.runOnIdle { assertEquals(listOf("instructions" to "en-GB", "directions" to "en"), spoken) }
        screenshot("inline-light")
        compose.runOnIdle { dark = true; rtl = true; scale = 1.3f }
        compose.waitForIdle()
        tapWord(sentence, "instructions")
        tapWord("كيف تنطق \"Entschuldigung\" بالألمانية؟", "Entschuldigung")
        tapWord("You should take care of your belongings.", "take care of")
        compose.runOnIdle { assertEquals("Entschuldigung" to "de", spoken[3]); assertEquals("take care of" to "en", spoken[4]) }
        screenshot("inline-dark-rtl-large")
    }

    @Test fun previewTargetsCanBeHeardEditedAndImported() {
        val spoken = mutableListOf<Pair<String, String>>()
        val card = ImportCard("qa", "Say Hallo", "Hello", null, null, emptyList(), pronunciationTargets = listOf(PronunciationTarget("front", "Hallo", "de")))
        val draft = ImportDraft("German", null, "Greetings", null, emptyList(), listOf(card), "language_learning", "de")
        var imported: ImportDraft? = null
        compose.setContent {
            RecallTheme {
                CompositionLocalProvider(LocalPronunciation provides { word, lang -> spoken += word to lang }) {
                    Surface { Column(Modifier.fillMaxSize().padding(RecallSpacing.md)) { ImportPreview(draft, {}) { imported = it } } }
                }
            }
        }
        tapWord("Say Hallo", "Hallo")
        compose.runOnIdle { assertEquals(listOf("Hallo" to "de"), spoken) }
        compose.onNodeWithContentDescription("Edit import card").performClick()
        compose.onNodeWithText("Pronunciation · 1 +").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithText("Import cards").performClick()
        compose.runOnIdle { assertTrue(imported!!.cards.single().pronunciationTargets.isEmpty()) }
    }

    @Test fun manualEditorAcceptsExactPhraseLanguageAndRepeatedOccurrence() {
        var saved: List<PronunciationTarget>? = null
        compose.setContent {
            RecallTheme {
                CardEditorSheet(
                    initial = CardEntity(lessonId = "", front = "Say Hallo, then Hallo", back = "مرحبًا"),
                    save = { _, _, _, _, targets -> saved = targets },
                    dismiss = {},
                )
            }
        }
        compose.onNodeWithText("Pronunciation · 0 +").performScrollTo().performClick()
        compose.onNodeWithText("Add pronunciation", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Exact word or phrase").performScrollTo().performTextInput("Hallo")
        compose.onNodeWithText("Language · e.g. en, en-GB, de").performScrollTo().performTextInput("de")
        compose.onNodeWithText("Occurrence · 1 is the first match").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Add target").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(PronunciationTarget("front", "Hallo", "de", 2)), saved) }
        screenshot("manual-editor")
    }

    @Test fun longReviewKeepsRevealAndRatingsReachableAtLargeFontScale() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = androidx.lifecycle.ViewModelStore().apply { put("recall", vm) }
        val dao = db.dao()
        val subject = SubjectEntity(name = "English")
        val lesson = LessonEntity(subjectId = subject.id, title = "Long reading", learningLanguage = "en")
        val card = CardEntity(lessonId = lesson.id, front = ("راجع هذه الفكرة مع directions ثم استرجعها بنفسك. ").repeat(18), back = ("Read the instructions carefully and compare the examples. ").repeat(25))
        dao.insertSubject(subject); dao.insertLesson(lesson); dao.insertCard(card); dao.saveState(ReviewStateEntity(cardId = card.id))
        dao.insertPronunciationTargets(listOf(PronunciationTarget("front", "directions").entity(card.id)))
        val cards = dao.lessonCards(lesson.id)
        compose.setContent {
            RecallTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface { ReviewScreen(cards, vm) {} }
                }
            }
        }
        try {
            compose.onNodeWithText("Show answer").assertIsDisplayed().performClick()
            compose.onNodeWithText("Again", substring = false).assertIsDisplayed()
            compose.onNodeWithText("Good", substring = false).assertIsDisplayed()
            screenshot("long-review-dark-rtl")
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun tappingInReviewDoesNotRevealRateOrChangeMemory() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = androidx.lifecycle.ViewModelStore().apply { put("recall", vm) }
        val dao = db.dao()
        val subject = SubjectEntity(name = "German")
        val lesson = LessonEntity(subjectId = subject.id, title = "Greetings", learningLanguage = "de")
        val card = CardEntity(lessonId = lesson.id, front = "Say Guten Morgen", back = "Good morning")
        dao.insertSubject(subject); dao.insertLesson(lesson); dao.insertCard(card)
        val state = ReviewStateEntity(cardId = card.id, reps = 5)
        dao.saveState(state)
        dao.insertPronunciationTargets(listOf(PronunciationTarget("front", "Guten Morgen").entity(card.id), PronunciationTarget("back", "Good morning", "en").entity(card.id)))
        val cards = dao.lessonCards(lesson.id)
        val spoken = mutableListOf<Pair<String, String>>()
        compose.setContent {
            RecallTheme {
                CompositionLocalProvider(LocalPronunciation provides { word, lang -> spoken += word to lang }) {
                    Surface { ReviewScreen(cards, vm) {} }
                }
            }
        }
        try {
            compose.waitUntil(5000) { compose.onAllNodes(hasText("Say Guten Morgen")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            tapWord(card.front, "Guten Morgen")
            compose.onNodeWithText("Show answer").assertIsDisplayed()
            compose.onNodeWithText("Good").assertDoesNotExist()
            assertEquals(state, dao.allStates().single())
            assertTrue(dao.allLogs().isEmpty())
            compose.onNodeWithText("Show answer").performClick()
            tapWord(card.back, "Good morning")
            assertEquals(state, dao.allStates().single())
            assertTrue(dao.allLogs().isEmpty())
            compose.runOnIdle { assertEquals(listOf("Guten Morgen" to "de", "Good morning" to "en"), spoken) }
            screenshot("review-answer")
            compose.onNodeWithText("Good", substring = false).performClick()
            compose.waitUntil(5000) { runBlocking { dao.allLogs().size == 1 } }
            assertEquals(6, dao.allStates().single().reps)
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
    }
}
