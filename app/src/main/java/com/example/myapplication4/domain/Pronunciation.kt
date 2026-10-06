package com.example.myapplication4.domain

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class PronunciationTarget(val side: String, val text: String, val language: String? = null, val occurrence: Int = 1)
data class ResolvedPronunciation(val target: PronunciationTarget, val start: Int, val end: Int, val language: String)
data class PronunciationValidation(val targets: List<PronunciationTarget>, val skipped: Int)

/** Exact UTF-16 ranges are resolved locally; no offsets supplied by an AI are trusted. */
object PronunciationResolver {
    fun languageTag(value: String?): String? = value?.takeIf { it.length in 2..50 && Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*").matches(it) }?.let {
        runCatching { Locale.Builder().setLanguageTag(it).build().takeUnless { locale -> locale.language == "und" || locale.language.isEmpty() }?.toLanguageTag() }.getOrNull()
    }

    fun resolve(text: String, side: String, targets: List<PronunciationTarget>, lessonLanguage: String? = null): List<ResolvedPronunciation> {
        val result = mutableListOf<ResolvedPronunciation>()
        targets.take(32).forEach { target ->
            if (target.side != side || side !in setOf("front", "back") || target.text.isBlank() || target.text.length > 160 || target.text.any { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' } || target.occurrence !in 1..1000) return@forEach
            val language = languageTag(target.language ?: lessonLanguage) ?: return@forEach
            var offset = 0
            var found = -1
            repeat(target.occurrence) {
                found = if (offset <= text.length) text.indexOf(target.text, offset) else -1
                offset = if (found >= 0) found + target.text.length else text.length + 1
            }
            if (found < 0) return@forEach
            val end = found + target.text.length
            if (result.any { found < it.end && end > it.start }) return@forEach
            result += ResolvedPronunciation(target, found, end, language)
        }
        return result.sortedBy { it.start }
    }

    fun validate(front: String, back: String, targets: List<PronunciationTarget>, lessonLanguage: String? = null): PronunciationValidation {
        val valid = resolve(front, "front", targets, lessonLanguage) + resolve(back, "back", targets, lessonLanguage)
        return PronunciationValidation(valid.map { it.target }, targets.size - valid.size)
    }

    fun parse(value: Any?, front: String, back: String, lessonLanguage: String?): PronunciationValidation {
        if (value == null || value == JSONObject.NULL) return PronunciationValidation(emptyList(), 0)
        if (value !is JSONArray) return PronunciationValidation(emptyList(), 1)
        var malformed = (value.length() - 32).coerceAtLeast(0)
        val candidates = (0 until minOf(value.length(), 32)).mapNotNull { i ->
            val obj = value.optJSONObject(i)
            val side = obj?.opt("side") as? String
            val text = obj?.opt("text") as? String
            val lang = obj?.opt("language")
            val occurrence = if (obj?.has("occurrence") == true) obj.opt("occurrence") else 1
            if (side == null || text == null || (lang != null && lang != JSONObject.NULL && lang !is String) || occurrence !is Number || occurrence.toDouble() != occurrence.toInt().toDouble()) {
                malformed++; null
            } else PronunciationTarget(side, text, lang as? String, occurrence.toInt())
        }
        val validated = validate(front, back, candidates, lessonLanguage)
        return validated.copy(skipped = validated.skipped + malformed)
    }
}

/** Only an offline voice in the requested language (and region, if given) is eligible. */
data class SpeechVoice(val name: String, val language: String, val networkRequired: Boolean, val installed: Boolean = true)
fun selectSpeechVoice(language: String, voices: List<SpeechVoice>, preferredName: String? = null): SpeechVoice? {
    val requested = Locale.forLanguageTag(PronunciationResolver.languageTag(language) ?: return null)
    return voices.filter {
        val locale = Locale.forLanguageTag(it.language)
        !it.networkRequired && it.installed && locale.language == requested.language &&
            (requested.country.isEmpty() || locale.country == requested.country) &&
            (requested.script.isEmpty() || locale.script == requested.script)
    }.sortedWith(compareByDescending<SpeechVoice> { it.name == preferredName }.thenBy { it.name }).firstOrNull()
}
