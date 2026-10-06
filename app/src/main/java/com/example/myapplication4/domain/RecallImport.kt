package com.example.myapplication4.domain

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

fun normalizedName(value: String) = Normalizer.normalize(value, Normalizer.Form.NFC).trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

fun subjectAiPrompt(subject: String, lesson: String? = null): String =
    AI_PROMPT + "\n\nEXISTING DESTINATION\nThe subject already exists. Append study content to this subject; never translate, abbreviate, or rename it. Set subject to exactly " +
        JSONObject.quote(subject) + ".\n" +
        (lesson?.let { "Append new cards to the existing lesson. Set lesson to exactly " + JSONObject.quote(it) + ". Do not replace existing content." }
            ?: "Create a lesson title from the supplied pages within this subject. Use chapter: null unless the user explicitly supplies a chapter.")

data class ImportCard(val type: String, val front: String, val back: String, val hint: String?, val source: String?, val tags: List<String>, val duplicate: Boolean = false, val included: Boolean = true, val pronunciationTargets: List<PronunciationTarget> = emptyList())
data class ImportDraft(val subject: String, val chapter: String?, val lesson: String, val summary: String?, val tags: List<String>, val cards: List<ImportCard>, val contentType: String = "general", val learningLanguage: String? = null, val pronunciationWarnings: Int = 0)
sealed interface ImportResult { data class Success(val draft: ImportDraft): ImportResult; data class Failure(val message: String, internal val diagnostic: String? = null): ImportResult }
object RecallImportParser {
    private const val maxCards = 500
    fun parse(raw: String, fallbackLanguage: String? = null): ImportResult { return try {
        val json = JSONObject(cleanJson(raw))
        if (json.optInt("version", -1) !in 1..2) return ImportResult.Failure("This file uses an unsupported Recall format version.")
        val subject = required(json, "subject", 120) ?: return ImportResult.Failure("A subject is required and must be under 120 characters.")
        val lesson = required(json, "lesson", 200) ?: return ImportResult.Failure("A lesson title is required and must be under 200 characters.")
        val learningLanguage = PronunciationResolver.languageTag(json.opt("learning_language") as? String)
            ?: PronunciationResolver.languageTag(fallbackLanguage)
        val contentType = if (json.optString("content_type") == "language_learning") "language_learning" else "general"
        var pronunciationWarnings = 0
        val seen = mutableSetOf<String>()
        val cards = json.optJSONArray("cards") ?: return ImportResult.Failure("No cards were found.")
        if (cards.length() == 0) return ImportResult.Failure("The cards list is empty.")
        if (cards.length() > maxCards) return ImportResult.Failure("An import can contain at most $maxCards cards.")
        val parsed = (0 until cards.length()).mapNotNull { index ->
            val card = cards.optJSONObject(index) ?: return@mapNotNull null
            val type = when (card.optString("type", "qa").trim().lowercase()) {
                "qa", "basic", "question_answer" -> "qa"
                "cloze", "fill_blank", "fill-in-the-blank" -> "cloze"
                else -> return@mapNotNull null
            }
            val frontRaw = required(card, "front", 6_000) ?: return@mapNotNull null
            val backRaw = required(card, "back", 12_000) ?: return@mapNotNull null
            if (ScienceTextProcessor.hasMalformedMarkers(frontRaw) || ScienceTextProcessor.hasMalformedMarkers(backRaw)) {
                return ImportResult.Failure("Card ${index + 1} has an incomplete or empty science notation marker.")
            }
            val front = ScienceTextProcessor.normalizeMarked(frontRaw)
            val back = ScienceTextProcessor.normalizeMarked(backRaw)
            val pronunciation = PronunciationResolver.parse(card.opt("pronunciation_targets"), front, back, learningLanguage)
            pronunciationWarnings += pronunciation.skipped
            val key = "$type|" + canonical(front)
            ImportCard(type, front, back, card.optNullable("hint", 2_000), card.optNullable("source_reference", 500), strings(card.optJSONArray("tags")), !seen.add(key), pronunciationTargets = pronunciation.targets)
        }
        if (parsed.isEmpty()) ImportResult.Failure("No valid question and answer pairs were found.")
        else ImportResult.Success(ImportDraft(subject, json.optNullable("chapter", 200), lesson, json.optNullable("lesson_summary", 6_000), strings(json.optJSONArray("tags")), parsed, contentType, learningLanguage, pronunciationWarnings))
    } catch (error: Exception) { ImportResult.Failure(explainInvalidJson(raw), "${error.javaClass.name}: ${error.message}") } }
    private fun cleanJson(raw: String): String {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        val fence = 96.toChar().toString().repeat(3)
        if (trimmed.startsWith(fence)) {
            val firstLine = trimmed.indexOf('\n')
            val end = trimmed.lastIndexOf(fence)
            if (firstLine >= 0 && end > firstLine) return trimmed.substring(firstLine + 1, end).trim()
        }
        val firstObject = trimmed.indexOf('{')
        if (firstObject >= 0 && (firstObject == 0 || !trimmed.substring(0, firstObject).contains('"'))) {
            completeObjectAt(trimmed, firstObject)?.let { return it }
        }
        return trimmed
    }
    private fun completeObjectAt(value: String, start: Int): String? {
        var depth = 0
        var quoted = false
        var escaped = false
        value.forEachIndexed { index, char ->
            if (index < start) return@forEachIndexed
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return value.substring(start, index + 1)
            }
        }
        return null
    }
    private fun explainInvalidJson(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "Paste or open a complete Recall JSON file first."
        if (!value.startsWith("{") && !value.startsWith("```")) {
            return "This is not a complete Recall JSON object. Copy from the opening { through the final }."
        }
        val stack = ArrayDeque<Char>()
        var quoted = false
        var escaped = false
        value.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> stack.addLast(char)
                '}' -> if (stack.lastOrNull() == '{') stack.removeLast()
                ']' -> if (stack.lastOrNull() == '[') stack.removeLast()
            }
        }
        if (quoted || stack.isNotEmpty()) {
            val missing = stack.reversed().joinToString("") { if (it == '{') "}" else "]" }
            return "The pasted JSON is incomplete or was cut off. It is missing ${if (quoted) "a closing quote" else missing}. Open the .json file directly or copy the entire response again."
        }
        return "The JSON structure is invalid. Open the original .json file directly, or copy the complete response without extra characters."
    }
    private fun required(o: JSONObject, key: String, max: Int): String? {
        if (!o.has(key) || o.isNull(key) || o.opt(key) !is String) return null
        return o.optString(key).trim().takeIf { it.isNotEmpty() && it.length <= max }
    }
    private fun JSONObject.optNullable(key: String, max: Int) = if (!has(key) || isNull(key) || opt(key) !is String) null else optString(key).trim().takeIf { it.isNotEmpty() && it.length <= max }
    private fun strings(a: JSONArray?): List<String> = a?.let { (0 until it.length()).mapNotNull { i -> a.opt(i)?.takeIf { it is String }?.toString()?.trim()?.removePrefix("#")?.takeIf { s -> s.isNotEmpty() && s.length <= 80 } }.distinctBy { canonical(it) } } ?: emptyList()
    private fun canonical(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().replace(Regex("\\s+"), " ").trim()
    fun fingerprint(card: ImportCard): String = MessageDigest.getInstance("SHA-256").digest((card.type + card.front.lowercase().trim() + card.back.lowercase().trim()).toByteArray()).joinToString("") { "%02x".format(it) }
}

const val AI_PROMPT = """You are creating a SMALL, HIGH-VALUE set of long-term active-recall cards from the study material attached to this conversation.

GOAL
Preserve the smallest set of knowledge the learner must retrieve to understand, reconstruct, and later apply the lesson. Recall is a long-term memory system, not a question bank. Favor quality, compression, and retention over coverage by volume. Read every supplied page before selecting cards, but do not make a card just because something appears in the source.

SELECT WHAT TO REMEMBER
- Silently separate (1) durable knowledge worth repeated retrieval, (2) useful context for lesson_summary, and (3) practice-only material that normally needs no card. Do not output these labels.
- Prioritize foundational definitions, rules and conditions, causes and mechanisms, consequences, relationships, commonly confused comparisons, exceptions, required classifications, important processes, formulas and their variables or conditions, misconceptions, and facts that other ideas depend on.
- Leave minor details, decorative examples, repeated explanations, obvious facts, and facts readily reconstructed from a more fundamental rule out of the cards. Preserve useful secondary context in lesson_summary, a compact overview rather than copied pages.
- Before keeping each card, ask silently: Would forgetting this weaken understanding? Does another card already test it? Will it still be worth reviewing in six months? If not, omit or merge it.

MEMORY CARDS, NOT PRACTICE EXERCISES
- Do not invent fixed grammar sentences, numerical problems, chemical exercises, or other arbitrary examples that ask the learner to solve, parse, calculate, or classify one instance. Repeated reviews can turn these into memorized answers instead of transferable knowledge.
- Ask for the general rule, decision criteria, relationship, procedure, reaction conditions, products, cause, trend, or exception needed to handle many examples.
- For Arabic grammar, avoid "أعرب المبتدأ في الجملة: العلم نور." Prefer "ما العلامة الأصلية لإعراب المبتدأ؟" or, when central to the lesson, "متى يجب تقديم الخبر على المبتدأ؟"
- In mathematics and physics, prefer what a formula expresses, when it applies, how variables relate, and common errors over multiple fixed numerical exercises. In chemistry, prefer conditions, products, oxidation/reduction relationships, reasons, trends, and exceptions over invented exercises.
- Make an example its own card only when it is explicitly required knowledge, canonical for a commonly confused distinction, expected to be memorized, or exposes an important exception or misconception. Otherwise a brief example may clarify an answer AFTER the general rule.

REDUNDANCY AND GRANULARITY
- Two cards overlap when answering one already supplies the other's answer without additional meaningful knowledge. Keep the strongest question; merge closely related facts that form one natural retrieval unit. Split only independent ideas that could be forgotten separately.
- "One concept per card" does not mean one sentence or tiny fact per card. Compare related conditions together when the answer remains easy to recall; split a long list of independent conditions when needed.
- Each card should usually be answerable mentally in about 5–30 seconds. Do not create reworded definitions, recognition variants, and example cards for the same underlying rule.
- Favor specific, self-contained questions: What is/causes/results from ...? Why ...? Under what conditions ...? When is ... required or impossible? How do X and Y differ? What are the essential steps? What exception applies?
- Avoid "Explain X", "Talk about X", yes/no prompts, and references to a previous card, textbook, diagram, or unseen example. Give enough context on the front to identify the question without revealing the answer.

CARD COUNT
- There is NO minimum card count and NO target number. A small lesson may justify only a few cards; a dense lesson may justify more. Never add weak cards to reach a quota or to cover every paragraph or bullet.
- Hard maximum: 40 high-value cards. If more seem necessary, remove weak facts, merge overlaps, prioritize foundations, and place secondary context in lesson_summary. Do not fill all 40 slots by default.

CONTENT RULES
1. Read every supplied page; do not invent absent or uncertain facts. Preserve the source's intended meaning, terminology, and language.
2. Questions must require recall rather than yes/no recognition. Answers should be concise but sufficient to reconstruct the idea.
3. Preserve Arabic and Latin terms, equations, chemical notation, superscripts, subscripts, units, punctuation, and code accurately. Follow SCIENCE NOTATION below for every formula.
4. Use "qa" for normal question/answer cards. Use "cloze" only when recovering a precise term or expression from context is genuinely useful; mark the missing text in front with {{...}} and put it in back.
5. Add a short hint only when it helps retrieval without revealing the answer. Otherwise use null.
6. Use source_reference only for a visible page, heading, figure, or section. Never fabricate a page number.

FINAL QUALITY FILTER
Silently review the complete deck before returning it. Delete or merge any card that tests a fixed practice example, repeats another card's knowledge, adds little to long-term mastery, has an unnecessarily detailed answer, or belongs in lesson_summary. If the review list feels intimidating for the lesson's size, compress it again.

JSON RULES
- Return exactly one JSON object and nothing else: no Markdown, introduction, commentary, or trailing text.
- Use valid JSON with double-quoted keys and strings. Escape embedded quotes, backslashes, and line breaks.
- Use null, not the strings "null", "N/A", or "-".
- Required non-empty fields: subject, lesson, every card.front, and every card.back.
- chapter may be null. lesson_summary is a compact overview, not copied pages.
- Card type must be exactly "qa" or "cloze".
- Tags are short reusable labels without #. Remove duplicate tags and duplicate cards.

SCIENCE NOTATION — REQUIRED FOR CHEMISTRY, PHYSICS, AND MATHEMATICS
- Wrap every complete chemical expression in [[chem:...]] and every complete mathematical or physical expression in [[math:...]]. Recall hides these markers and renders the enclosed expression as one left-to-right unit, even inside Arabic text.
- Put the entire equation inside ONE pair of markers. Never split reactants, products, coefficients, states, an equality, or its operators across multiple marked spans.
- Use real Unicode symbols in the marked expression. Use → for a reaction, ⇌ for equilibrium, − for a minus sign, × or · for multiplication, ÷ or / for division, ±, ≤, ≥, ≠, √, ∫, ∑, Δ, π, and θ where appropriate.
- Use Unicode subscripts and superscripts: H₂O, CO₂, Fe³⁺, SO₄²⁻, x², m·s⁻². Keep leading stoichiometric coefficients at normal height: 2Fe₂O₃, not ₂Fe₂O₃.
- Preserve phase labels exactly: (s), (l), (g), and (aq). Preserve brackets and charge signs. Balance chemical equations before returning them.
- Do not use LaTeX commands, MathML, HTML tags, dollar-sign delimiters, underscores for subscripts, carets for powers, or ASCII arrows such as ->. Do not translate element symbols or variables.
- Keep an equation on its own line when it is the main answer. Explanatory Arabic or English prose goes outside the markers.
- Correct examples:
  "back": "[[chem:2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)]]\nعند نحو 700 °C"
  "back": "طبق [[math:V = IR]] حيث V فرق الجهد."
  "back": "[[math:x = (−b ± √(b² − 4ac)) / 2a]]"
- Before returning JSON, verify visually that every opening [[chem: or [[math: has exactly one closing ]], and that no Arabic prose is inside a marked expression.

PRONUNCIATION — LANGUAGE LESSONS ONLY
- If the material primarily teaches vocabulary, grammar, reading comprehension, synonyms, antonyms, or expressions, set content_type to "language_learning" and learning_language to its BCP-47 code: en, en-US, en-GB, de, fr, es, it, ar, etc.
- Otherwise set content_type to "general", learning_language to null, and omit pronunciation_targets. Do not annotate chemistry, physics, formulas, or code automatically.
- For language cards, mark only important taught vocabulary, compared words, unfamiliar terms, phrasal verbs, and SHORT expressions useful to hear. Usually 0–4 targets; zero is fine. Never mark every word, articles, connectors, punctuation, or paragraphs.
- On language cards only, pronunciation_targets is an optional array on that card. Each target is {"side":"front","text":"instructions","occurrence":1}. side is exactly "front" or "back". text must match an EXACT visible substring on that side, including case and punctuation. Do not add markup, offsets, or pronunciation symbols to card text.
- occurrence is the 1-based occurrence of that exact substring, counted from the start of that side. Resolve repeated words deliberately. Do not overlap or duplicate targets.
- Target language may be omitted to inherit learning_language. Supply "language" only for a different language or regional voice. Never guess a language from the app interface.
- Example: What is the difference between “instructions” and “directions”? Mark only instructions and directions; NOT What, is, the, difference, between, or and.
- Phrases such as "take care of" or "auf Wiedersehen" are one target, not separate words.
- Verify every target against the final front/back text before returning JSON.

OUTPUT SCHEMA
{
  "version": 2,
  "subject": "Subject name",
  "chapter": null,
  "lesson": "Lesson title",
  "lesson_summary": "Compact summary of the lesson",
  "content_type": "general",
  "learning_language": null,
  "tags": ["topic"],
  "cards": [
    {
      "type": "qa",
      "front": "A clear, self-contained question",
      "back": "A concise, accurate answer",
      "hint": null,
      "source_reference": null,
      "tags": []
    }
  ]
}"""
