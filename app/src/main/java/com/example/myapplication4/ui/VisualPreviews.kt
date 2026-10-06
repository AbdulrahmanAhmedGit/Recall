package com.example.myapplication4.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.myapplication4.data.LessonOverview
import com.example.myapplication4.data.SubjectEntity
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*

@Preview(name = "Components · Light", widthDp = 360, showBackground = true)
@Preview(name = "Components · Dark RTL", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "ar")
@Composable private fun ComponentPreview() { RecallTheme { Column(Modifier.fillMaxSize().padding(RecallSpacing.ml), verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) { SubjectRow(SubjectEntity(name = "Chemistry - العناصر الانتقالية", accent = "purple"), 8, 64, 12) {}; LessonRow(LessonOverview("1", "Electric Current · التيار الكهربائي", null, "Physics", "s", "Chapter 2", "blue", 18, 6, 8, 2, null, null)) {}; Spacer(Modifier.weight(1f)); RecallDock(RecallDestination.Today, {}) } } }

@Preview(name = "Bidi laboratory · LTR", widthDp = 360, showBackground = true)
@Preview(name = "Bidi laboratory · RTL Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "ar")
@Composable private fun BidiLaboratoryPreview() { RecallTheme { Column(Modifier.fillMaxSize().padding(RecallSpacing.ml), verticalArrangement = Arrangement.spacedBy(RecallSpacing.md)) { listOf("العناصر الانتقالية", "Transition Elements", "راجع درس Transition Elements اليوم", "توزيع Mn²⁺ هو [Ar] 3d⁵", "طبق قانون V = IR", "المراجعة القادمة الساعة 8:30 PM", "استخدم Kotlin مع Jetpack Compose", "هل [Ar] 3d⁵ أكثر استقرارًا من [Ar] 3d⁴؟").forEach { BidiAwareText(it, style = MaterialTheme.typography.bodyLarge) } } } }

@Preview(name = "Forced RTL structure", widthDp = 320, fontScale = 1.3f, showBackground = true)
@Composable private fun ForcedRtlPreview() { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { BidiLaboratoryPreview() } }

@Preview(name = "Pronunciation · English", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Pronunciation · RTL dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "ar")
@Preview(name = "Pronunciation · Small large type", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "ar")
@Composable
private fun PronunciationPreview() {
    RecallTheme {
        androidx.compose.material3.Surface {
            Column(Modifier.padding(RecallSpacing.ml), verticalArrangement = Arrangement.spacedBy(RecallSpacing.lg)) {
                androidx.compose.material3.Text("QUESTION", style = MaterialTheme.typography.labelSmall)
                PronounceableStudyText(
                    "What is the difference between “instructions” and “directions”?", "front",
                    listOf(com.example.myapplication4.domain.PronunciationTarget("front", "instructions", "en"), com.example.myapplication4.domain.PronunciationTarget("front", "directions", "en")),
                    style = MaterialTheme.typography.headlineMedium,
                )
                PronounceableStudyText(
                    "كيف تنطق \"Entschuldigung\" بالألمانية؟", "front",
                    listOf(com.example.myapplication4.domain.PronunciationTarget("front", "Entschuldigung", "de")),
                )
                PronounceableStudyText(
                    "ما معنى عبارة take care of؟", "back",
                    listOf(com.example.myapplication4.domain.PronunciationTarget("back", "take care of", "en")),
                )
                BidiAwareText("توزيع Mn²⁺ هو [Ar] 3d⁵ · V = IR")
            }
        }
    }
}
