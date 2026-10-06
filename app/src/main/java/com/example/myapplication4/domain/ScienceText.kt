package com.example.myapplication4.domain

private const val LRI = '\u2066'
private const val PDI = '\u2069'
private val Arabic = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]")
private val TechnicalRun = Regex("(?<!\\u2066)(?:https?://\\S+|\\[[A-Za-z]{1,3}](?:\\s+[0-9][spdf][⁰¹²³⁴⁵⁶⁷⁸⁹0-9]+)*|[A-Za-z][A-Za-z0-9._/+-]*[₀₁₂₃₄₅₆₇₈₉⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻]*(?:\\s*=\\s*[A-Za-z0-9πθ]+)?|[0-9]+[πθ][A-Za-z0-9]*|[0-9]{1,2}:[0-9]{2}\\s*(?:AM|PM))")
private val ScienceMarker = Regex("\\[\\[(chem|math):([\\s\\S]*?)]]", RegexOption.IGNORE_CASE)
private val StrongScienceOperator = Regex("(?:->|<=>|<->|[=→⇌↔×÷·*/^_∫∑Σ√≤≥≠±])")
private val ChemicalToken = Regex("(?:[A-Z][a-z]?[0-9₀-₉]+|(?:[A-Z][a-z]?){2,}|\\((?:s|l|g|aq)\\))")
private val Measurement = Regex("(?<![A-Za-z0-9])[-−+]?[0-9]+(?:[.,][0-9]+)?\\s*°?\\s*(?:°C|°F|K|kg|mol|Pa|kPa|J|kJ|V|mV|A|mA|W|Hz|N|m|cm|mm|km|s|ms)(?:[·/]\\s*[A-Za-z]+[⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻]*)?(?![A-Za-z0-9])")

enum class ScienceKind { CHEMISTRY, MATHEMATICS, INFERRED }
data class ScienceRange(val start: Int, val end: Int, val kind: ScienceKind)
data class StudyDisplayText(val text: String, val rawPositions: IntArray, val scienceRanges: List<ScienceRange> = emptyList()) {
    fun displayRange(start: Int, end: Int): IntRange = rawPositions[start] until (rawPositions[end - 1] + 1)
}
private data class VisibleText(val value: String, val rawToVisible: IntArray, val explicit: List<ScienceRange>)

/** Pure, deterministic, offline processing for mixed Arabic scientific study text. */
object ScienceTextProcessor {
    fun process(raw: String): StudyDisplayText {
        if (raw.isEmpty()) return StudyDisplayText("", IntArray(0))
        val visible = removeMarkers(raw)
        val inferred = inferScience(visible.value).filter { candidate ->
            visible.explicit.none { candidate.start < it.end && candidate.end > it.start }
        }
        val science = mergeScience(visible.explicit + inferred)
        val isolateRanges = isolationRanges(visible.value, science)
        val starts = isolateRanges.associateBy { it.first }
        val visibleToFinal = IntArray(visible.value.length)
        val output = StringBuilder()
        var index = 0
        while (index < visible.value.length) {
            val range = starts[index]
            if (range == null) {
                visibleToFinal[index] = output.length
                output.append(visible.value[index++])
            } else {
                output.append(LRI)
                while (index <= range.last) {
                    visibleToFinal[index] = output.length
                    output.append(visible.value[index++])
                }
                output.append(PDI)
            }
        }
        val rawPositions = IntArray(raw.length) { rawIndex ->
            val visibleIndex = visible.rawToVisible[rawIndex].coerceIn(0, (visible.value.length - 1).coerceAtLeast(0))
            if (visible.value.isEmpty()) 0 else visibleToFinal[visibleIndex]
        }
        val finalScience = science.map { range -> ScienceRange(visibleToFinal[range.start], visibleToFinal[range.end - 1] + 1, range.kind) }
        return StudyDisplayText(output.toString(), rawPositions, finalScience)
    }

    /** Repairs common AI notation inside supported markers before it is stored. */
    fun normalizeMarked(raw: String): String = ScienceMarker.replace(raw) { match ->
        val kind = match.groups[1]!!.value.lowercase()
        val content = match.groups[2]!!.value
        "[[$kind:" + normalizeExpression(content, kind == "chem") + "]]"
    }

    fun hasMalformedMarkers(raw: String): Boolean {
        val complete = ScienceMarker.findAll(raw).toList()
        val openings = Regex("\\[\\[(?:chem|math):", RegexOption.IGNORE_CASE).findAll(raw).count()
        return openings != complete.size || complete.any { it.groups[2]!!.value.isBlank() }
    }

    private fun normalizeExpression(value: String, chemistry: Boolean): String {
        var result = value.trim()
            .replace("<=>", "⇌").replace("<->", "↔").replace("->", "→")
            .replace("<=", "≤").replace(">=", "≥").replace("!=", "≠")
            .replace("\\times", "×").replace("\\cdot", "·").replace("\\pm", "±")
            .replace("\\rightarrow", "→").replace("\\leftrightarrow", "↔")
        result = replaceScripts(result)
        if (chemistry) result = chemicalSubscripts(result)
        return result
    }

    private fun replaceScripts(value: String): String {
        val subs = "0123456789+-".zip("₀₁₂₃₄₅₆₇₈₉₊₋").toMap()
        val supers = "0123456789+-".zip("⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻").toMap()
        return Regex("([_^])\\{?([0-9+-]+)\\}?").replace(value) { match ->
            val map = if (match.groupValues[1] == "_") subs else supers
            match.groupValues[2].map { map[it] ?: it }.joinToString("")
        }
    }

    private fun chemicalSubscripts(value: String): String {
        val subs = "0123456789".zip("₀₁₂₃₄₅₆₇₈₉").toMap()
        val result = StringBuilder()
        var subscriptRun = false
        value.forEachIndexed { index, char ->
            val previous = value.getOrNull(index - 1)
            val shouldLower = char.isDigit() && (subscriptRun || (previous != null && (previous.isLetter() || previous == ')')))
            subscriptRun = shouldLower
            result.append(if (shouldLower) subs[char] else char)
        }
        return result.toString()
    }

    private fun removeMarkers(raw: String): VisibleText {
        val mapping = IntArray(raw.length)
        val explicit = mutableListOf<ScienceRange>()
        val output = StringBuilder()
        var cursor = 0
        ScienceMarker.findAll(raw).forEach { match ->
            while (cursor < match.range.first) { mapping[cursor] = output.length; output.append(raw[cursor++]) }
            val content = match.groups[2]!!
            val start = output.length
            while (cursor < content.range.first) mapping[cursor++] = start
            while (cursor <= content.range.last) { mapping[cursor] = output.length; output.append(raw[cursor++]) }
            val end = output.length
            while (cursor <= match.range.last) mapping[cursor++] = (end - 1).coerceAtLeast(0)
            if (end > start) explicit += ScienceRange(start, end, if (match.groups[1]!!.value.equals("chem", true)) ScienceKind.CHEMISTRY else ScienceKind.MATHEMATICS)
        }
        while (cursor < raw.length) { mapping[cursor] = output.length; output.append(raw[cursor++]) }
        return VisibleText(output.toString(), mapping, explicit)
    }

    private fun inferScience(value: String): List<ScienceRange> {
        val result = Measurement.findAll(value)
            .map { ScienceRange(it.range.first, it.range.last + 1, ScienceKind.INFERRED) }
            .toMutableList()
        Regex("[^\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\n]+").findAll(value).forEach { match ->
            val leading = match.value.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return@forEach
            val trailing = match.value.indexOfLast { !it.isWhitespace() }
            val candidate = match.value.substring(leading, trailing + 1)
            val scientific = StrongScienceOperator.containsMatchIn(candidate) && candidate.any { it.isLetterOrDigit() }
            val compactFormula = ChemicalToken.containsMatchIn(candidate) && candidate.none { it == '؟' }
            if ((scientific || compactFormula) && candidate.length >= 2) result += ScienceRange(match.range.first + leading, match.range.first + trailing + 1, ScienceKind.INFERRED)
        }
        return result
    }

    private fun mergeScience(ranges: List<ScienceRange>) = ranges.sortedBy { it.start }.fold(mutableListOf<ScienceRange>()) { result, range ->
        val previous = result.lastOrNull()
        if (previous != null && range.start <= previous.end) result[result.lastIndex] = previous.copy(end = maxOf(previous.end, range.end)) else result += range
        result
    }

    private fun isolationRanges(value: String, science: List<ScienceRange>): List<IntRange> {
        val ranges = science.map { it.start until it.end }.toMutableList()
        if (Arabic.containsMatchIn(value)) {
            ranges += TechnicalRun.findAll(value)
                .map { it.range.first until (it.range.last + 1) }
                .filter { technical -> science.none { technical.first < it.end && technical.last + 1 > it.start } }
                .toList()
        }
        return ranges.sortedBy { it.first }.fold(mutableListOf()) { result, range ->
            val previous = result.lastOrNull()
            val gapStart = previous?.let { it.last + 1 } ?: 0
            val overlaps = previous != null && range.first <= previous.last
            val bridge = previous != null && range.first >= gapStart && value.substring(gapStart, range.first)
                .all { it == ' ' || it == '\t' || it in "—–-+=*/,:;.!?()[]{}\\\"'→⇌↔" }
            if (overlaps || bridge) result[result.lastIndex] = previous!!.first..maxOf(previous.last, range.last)
            else if (previous == null || range.first >= previous.last) result += range
            result
        }
    }
}
