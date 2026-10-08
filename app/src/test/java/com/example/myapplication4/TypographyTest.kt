package com.example.myapplication4

import com.example.myapplication4.ui.design.*
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test

class TypographyTest {
    private fun styles(t: Typography): List<TextStyle> = listOf(t.displayLarge, t.displayMedium, t.displaySmall,
        t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall,
        t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall)

    @Test fun nonArabicUsesBundledInterForEveryRole() {
        listOf("en", "en-US", "es", "fr", "de").forEach { code ->
            styles(recallTypography(code)).forEach { assertEquals(InterFont, it.fontFamily) }
        }
    }

    @Test fun studyContentUsesCairoForArabicWithoutChangingTextOrScienceSpans() {
        val source = "راجع قانون [[math:V = IR]] مع Kotlin وMn²⁺ اليوم"
        val display = studyDisplayText(source)
        val annotated = display.annotated()
        assertEquals(display.text, annotated.text)
        val arabicIndex = annotated.text.indexOf("راجع")
        val arabicStyle = annotated.spanStyles.first { it.start <= arabicIndex && it.end > arabicIndex }.item
        assertEquals(CairoFont, arabicStyle.fontFamily)
        assertEquals(0.sp, arabicStyle.letterSpacing)
        val latinIndex = annotated.text.indexOf("Kotlin")
        assertFalse(annotated.spanStyles.any { it.item.fontFamily == CairoFont && it.start <= latinIndex && it.end > latinIndex })
        display.scienceRanges.forEach { range ->
            assertTrue(annotated.spanStyles.any { it.start == range.start && it.end == range.end && it.item.fontFamily == FontFamily.SansSerif })
        }
        assertTrue(studyDisplayText("English with café and naïve").annotated().spanStyles.isEmpty())
    }

    @Test fun arabicUsesBundledCairoForEveryRoleAndPreservesTypeScale() {
        for (code in listOf("ar", "ar-EG")) {
            val arabic = styles(recallTypography(code))
            arabic.forEach { assertEquals(CairoFont, it.fontFamily) }
            arabic.zip(styles(RecallTypography)).forEach { (ar, original) ->
                assertEquals(original.fontSize, ar.fontSize)
                assertEquals(original.lineHeight, ar.lineHeight)
                assertEquals(original.fontWeight, ar.fontWeight)
            }
        }
        listOf("en", "es", "fr", "de").forEach { assertSame(RecallTypography, recallTypography(it)) }
    }
}
