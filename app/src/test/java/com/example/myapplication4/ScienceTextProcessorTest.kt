package com.example.myapplication4

import com.example.myapplication4.domain.ScienceKind
import com.example.myapplication4.domain.ScienceTextProcessor
import com.example.myapplication4.domain.ImportResult
import com.example.myapplication4.domain.RecallImportParser
import com.example.myapplication4.ui.design.studyDisplayText
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ScienceTextProcessorTest {
    private fun plain(value: String) = value.replace("\u2066", "").replace("\u2069", "")

    @Test fun explicitChemicalEquationIsOneAtomicLtrRangeAndMarkersAreHidden() {
        val raw = "المعادلة:\n[[chem:2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)]]\nعند 700 °C"
        val result = ScienceTextProcessor.process(raw)
        assertEquals("المعادلة:\n2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)\nعند 700 °C", plain(result.text))
        assertFalse(result.text.contains("[[chem:"))
        assertEquals(ScienceKind.CHEMISTRY, result.scienceRanges.first().kind)
        assertEquals("2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)", result.text.substring(result.scienceRanges.first().start, result.scienceRanges.first().end))
        assertTrue(result.text.contains("\u20662Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)\u2069"))
    }

    @Test fun explicitMathPreservesOperatorsPowersAndArabicAroundIt() {
        val result = ScienceTextProcessor.process("طبق [[math:x = (−b ± √(b² − 4ac)) / 2a]] ثم استخدم [[math:V = IR]].")
        assertEquals("طبق x = (−b ± √(b² − 4ac)) / 2a ثم استخدم V = IR.", plain(result.text))
        assertEquals(2, result.scienceRanges.count { it.kind == ScienceKind.MATHEMATICS })
        assertTrue(result.text.contains("\u2066x = (−b ± √(b² − 4ac)) / 2a\u2069"))
        assertTrue(result.text.contains("\u2066V = IR\u2069"))
    }

    @Test fun legacyUnmarkedReactionIsDetectedAsOneRun() {
        val equation = "2Fe2O3(s) + 3CO(g) -> 4Fe(s) + 3CO2(g)"
        val result = ScienceTextProcessor.process("الناتج $equation عند التسخين")
        assertTrue(result.text.contains("\u2066$equation\u2069"))
        assertEquals(equation, result.text.substring(result.scienceRanges.single().start, result.scienceRanges.single().end))
    }

    @Test fun scientificMeasurementsKeepNumberSymbolAndUnitInOrder() {
        val result = ScienceTextProcessor.process("عند درجة أعلى من 700 °C وبضغط 120 kPa خلال 5 ms")
        assertTrue(result.text.contains("\u2066700 °C\u2069"))
        assertTrue(result.text.contains("\u2066120 kPa\u2069"))
        assertTrue(result.text.contains("\u20665 ms\u2069"))
    }

    @Test fun malformedMarkersNeverDeleteContent() {
        val raw = "راجع [[chem:H₂O + CO₂"
        assertTrue(ScienceTextProcessor.hasMalformedMarkers(raw))
        assertTrue(ScienceTextProcessor.hasMalformedMarkers("[[math:   ]]"))
        assertEquals(raw, plain(ScienceTextProcessor.process(raw).text))
    }

    @Test fun pronunciationRawOffsetsRemainExactOutsideScienceMarkup() {
        val raw = "Say voltage ثم [[math:V = IR]] وبعدها current"
        val display = studyDisplayText(raw)
        val start = raw.indexOf("voltage")
        assertEquals("voltage", display.text.substring(display.displayRange(start, start + 7)))
        val after = raw.indexOf("current")
        assertEquals("current", display.text.substring(display.displayRange(after, after + 7)))
    }

    @Test fun commonAsciiScienceNotationIsNormalizedInsideMarkers() {
        assertEquals(
            "[[chem:2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)]]",
            ScienceTextProcessor.normalizeMarked("[[chem:2Fe2O3(s) + 3CO(g) -> 4Fe(s) + 3CO2(g)]]"),
        )
        assertEquals("[[math:x² + y₂ ≤ 4]]", ScienceTextProcessor.normalizeMarked("[[math:x^2 + y_2 <= 4]]"))
        assertEquals("[[chem:Ag₂ + C₁₂H₂₂O₁₁]]", ScienceTextProcessor.normalizeMarked("[[chem:Ag2 + C12H22O11]]"))
        assertEquals("outside x^2", ScienceTextProcessor.normalizeMarked("outside x^2"))
    }

    @Test fun importNormalizesSupportedScienceAndRejectsBrokenMarkers() {
        fun json(answer: String) = """{"version":2,"subject":"Chemistry","lesson":"Iron","cards":[{"type":"qa","front":"Equation?","back":"$answer"}]}"""
        val valid = RecallImportParser.parse(json("[[chem:2Fe2O3(s) + 3CO(g) -> 4Fe(s) + 3CO2(g)]]")) as ImportResult.Success
        assertEquals("[[chem:2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)]]", valid.draft.cards.single().back)
        val invalid = RecallImportParser.parse(json("[[math:V = IR")) as ImportResult.Failure
        assertTrue(invalid.message.contains("Card 1"))
    }

    @Test fun correctedIronLessonSampleImportsEveryOriginalCard() {
        val sample = listOf(
            File("recall-imports/chemistry-iron-lesson-v2-science.json"),
            File("../recall-imports/chemistry-iron-lesson-v2-science.json"),
        ).first { it.isFile }
        val parsed = RecallImportParser.parse(sample.readText()) as ImportResult.Success
        assertEquals("Chemistry", parsed.draft.subject)
        assertEquals("الباب الأول", parsed.draft.chapter)
        assertEquals(88, parsed.draft.cards.size)
        assertTrue(parsed.draft.cards.any { it.back.contains("[[chem:Fe₂O₃(s) + 3CO(g) → 2Fe(s) + 3CO₂(g)]]") })
    }
}
