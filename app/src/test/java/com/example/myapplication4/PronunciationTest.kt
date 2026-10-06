package com.example.myapplication4

import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.design.studyDisplayText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PronunciationTest {
    private fun draft(version: Int = 2, targets: Any? = null) = JSONObject()
        .put("version", version).put("subject", "English").put("lesson", "Vocabulary")
        .put("content_type", "language_learning").put("learning_language", "en-GB")
        .put("cards", JSONArray().put(JSONObject().put("front", "instructions and directions")
            .put("back", "Take care of your belongings.").put("pronunciation_targets", targets)))

    @Test fun exactOccurrencesPhrasesAndBothSides() {
        val front = "hello, hello! Take care of yourself."
        val targets = listOf(PronunciationTarget("front", "hello", "en", 2), PronunciationTarget("front", "Take care of", "en"), PronunciationTarget("back", "Hallo", "de"))
        val resolved = PronunciationResolver.resolve(front, "front", targets)
        assertEquals(listOf("hello", "Take care of"), resolved.map { front.substring(it.start, it.end) })
        assertEquals(7, resolved.first().start)
        assertEquals(1, PronunciationResolver.resolve("Hallo!", "back", targets).size)
    }

    @Test fun invalidDuplicatesOverlapsAndCaseMismatchAreSkipped() {
        val candidates = listOf(
            PronunciationTarget("front", "take care", "en"), PronunciationTarget("front", "care", "en"),
            PronunciationTarget("front", "take care", "en"), PronunciationTarget("front", "Take", "en"),
            PronunciationTarget("front", "care", "en", 3), PronunciationTarget("front", "", "en"),
            PronunciationTarget("wrong", "care", "en"), PronunciationTarget("back", "care", "bad_code"),
        )
        val result = PronunciationResolver.validate("take care", "care", candidates)
        assertEquals(1, result.targets.size)
        assertEquals(7, result.skipped)
    }

    @Test fun languageInheritanceNeverFallsBackToDeviceLanguage() {
        val target = PronunciationTarget("front", "Hallo")
        assertTrue(PronunciationResolver.resolve("Hallo", "front", listOf(target)).isEmpty())
        assertEquals("de-DE", PronunciationResolver.resolve("Hallo", "front", listOf(target), "de-DE").single().language)
        assertTrue(PronunciationResolver.resolve("Hallo", "front", listOf(target.copy(language = "not_a_tag")), "de").isEmpty())
        assertNull(PronunciationResolver.languageTag("und"))
        assertEquals("en-GB", PronunciationResolver.languageTag("en-gb"))
    }

    @Test fun bilingualOffsetsStayOnExactTargetsAfterIsolation() {
        listOf(
            "ما معنى كلمة \"environment\"؟" to "environment",
            "كيف تنطق \"Entschuldigung\" بالألمانية؟" to "Entschuldigung",
            "الفرق بين \"instructions\" و \"directions\" هو..." to "directions",
            "🧠 قل take care of ثم تابع" to "take care of",
            "قانون Ohm هو V = IR" to "V = IR",
            "توزيع Mn²⁺ هو [Ar] 3d⁵" to "[Ar] 3d⁵",
        ).forEach { (raw, target) ->
            val resolved = PronunciationResolver.resolve(raw, "front", listOf(PronunciationTarget("front", target, "en"))).single()
            val display = studyDisplayText(raw)
            val range = display.displayRange(resolved.start, resolved.end)
            assertEquals(target, display.text.substring(range).replace("\u2066", "").replace("\u2069", ""))
            assertEquals(raw, display.text.replace("\u2066", "").replace("\u2069", ""))
        }
    }

    @Test fun v2ImportsValidTargetsAndSkipsMalformedMetadataWithoutLosingCards() {
        val targets = JSONArray()
            .put(JSONObject().put("side", "front").put("text", "instructions").put("occurrence", 1))
            .put(JSONObject().put("side", "front").put("text", "missing"))
            .put(JSONObject().put("side", "back").put("text", "Take care of").put("language", 42))
            .put(JSONObject().put("side", "front").put("text", "directions").put("occurrence", 1.5))
            .put("invalid")
        val result = RecallImportParser.parse(draft(targets = targets).toString()) as ImportResult.Success
        assertEquals(1, result.draft.cards.size)
        assertEquals(1, result.draft.cards.single().pronunciationTargets.size)
        assertEquals(4, result.draft.pronunciationWarnings)
        assertEquals("en-GB", result.draft.learningLanguage)
        assertEquals("language_learning", result.draft.contentType)
    }

    @Test fun oldImportsAndUnrelatedScienceStayPlain() {
        val root = draft(1).apply { remove("content_type"); remove("learning_language") }
        val result = RecallImportParser.parse(root.toString()) as ImportResult.Success
        assertTrue(result.draft.cards.single().pronunciationTargets.isEmpty())
        assertEquals("general", result.draft.contentType)
        assertTrue(PronunciationResolver.resolve("Mn²⁺ [Ar] 3d⁵ V = IR", "front", emptyList()).isEmpty())
        val malformed = RecallImportParser.parse(draft(targets = "wrong").toString()) as ImportResult.Success
        assertEquals(1, malformed.draft.pronunciationWarnings)
        assertEquals(1, malformed.draft.cards.size)
    }

    @Test fun offlineVoiceSelectionHonorsLanguageRegionAndInstalledData() {
        val voices = listOf(
            SpeechVoice("english", "en-US", false), SpeechVoice("british-online", "en-GB", true),
            SpeechVoice("german", "de-DE", false), SpeechVoice("british-missing", "en-GB", false, false),
        )
        assertEquals("german", selectSpeechVoice("de", voices)?.name)
        assertNull(selectSpeechVoice("en-GB", voices))
        assertNull(selectSpeechVoice("fr", voices))
        assertEquals("english", selectSpeechVoice("en", voices)?.name)
        assertEquals("british", selectSpeechVoice("en-GB", voices + SpeechVoice("british", "en-GB", false))?.name)
    }

    @Test fun backupRoundTripPreservesTargetsLanguageAndSpeechRateAndReadsOldBackup() {
        val subject = SubjectEntity(name = "German")
        val lesson = LessonEntity(subjectId = subject.id, title = "Greetings", contentType = "language_learning", learningLanguage = "de")
        val card = CardEntity(lessonId = lesson.id, front = "Guten Morgen", back = "Good morning")
        val target = PronunciationTarget("front", "Guten Morgen").entity(card.id)
        val data = BackupData(listOf(subject), emptyList(), listOf(lesson), listOf(card), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), pronunciationTargets = listOf(target))
        val json = RecallBackupCodec.encode(data, UserSettings(speechRate = .8f))
        val decoded = RecallBackupCodec.decode(json) as BackupResult.Success
        assertEquals(data, decoded.data)
        assertEquals(.8f, decoded.settings.speechRate)
        val old = JSONObject(json).put("version", 1).apply { remove("pronunciation_targets") }
        old.getJSONArray("lessons").getJSONObject(0).apply { remove("learning_language"); remove("content_type") }
        old.getJSONObject("settings").remove("speech_rate")
        val legacy = RecallBackupCodec.decode(old.toString()) as BackupResult.Success
        assertTrue(legacy.data.pronunciationTargets.isEmpty())
        assertNull(legacy.data.lessons.single().learningLanguage)
        assertEquals(1f, legacy.settings.speechRate)
    }

    @Test fun generationPromptUsesSupportedSchemaAndHighValueSelectionRules() {
        val schema = JSONObject(AI_PROMPT.substringAfter("OUTPUT SCHEMA\n"))
        val parsed = RecallImportParser.parse(schema.toString()) as ImportResult.Success
        assertEquals(2, schema.getInt("version"))
        assertEquals("general", parsed.draft.contentType)
        assertNull(parsed.draft.learningLanguage)
        assertEquals("Compact summary of the lesson", parsed.draft.summary)
        assertEquals("qa", parsed.draft.cards.single().type)
        assertEquals(listOf("topic"), parsed.draft.tags)
        assertTrue(AI_PROMPT.contains("Return exactly one JSON object and nothing else"))
        assertTrue(AI_PROMPT.contains("NO minimum card count"))
        assertTrue(AI_PROMPT.contains("Hard maximum: 40 high-value cards"))
        assertFalse(AI_PROMPT.contains("80 high-value cards"))
        assertTrue(AI_PROMPT.contains("Two cards overlap when answering one already supplies the other's answer"))
        assertTrue(AI_PROMPT.contains("Do not invent fixed grammar sentences, numerical problems, chemical exercises"))
        assertTrue(AI_PROMPT.contains("Preserve useful secondary context in lesson_summary"))
        assertTrue(AI_PROMPT.contains("mark the missing text in front with {{...}}"))
        assertTrue(AI_PROMPT.contains("0–4"))
        assertTrue(AI_PROMPT.contains("EXACT visible substring"))
        assertTrue(AI_PROMPT.contains("pronunciation_targets is an optional array on that card"))
        assertTrue(AI_PROMPT.contains("[[chem:2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)]]"))
        assertTrue(AI_PROMPT.contains("[[math:V = IR]]"))
        assertTrue(AI_PROMPT.contains("Do not use LaTeX commands"))
        val destinationPrompt = subjectAiPrompt("English")
        assertTrue(destinationPrompt.startsWith(AI_PROMPT))
        assertTrue(destinationPrompt.contains("EXISTING DESTINATION"))
        assertTrue(destinationPrompt.contains("Hard maximum: 40 high-value cards"))
    }
}
