package com.example.myapplication4

import com.example.myapplication4.ui.design.*
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.*
import org.junit.Test

class TypographyTest {
    private fun styles(t: Typography): List<TextStyle> = listOf(t.displayLarge, t.displayMedium, t.displaySmall,
        t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall,
        t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall)

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
