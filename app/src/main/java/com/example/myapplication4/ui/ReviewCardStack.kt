package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.myapplication4.data.CardWithLesson
import com.example.myapplication4.domain.PronunciationTarget
import com.example.myapplication4.ui.design.*

/** Rear sheets represent remaining cards, never expose upcoming answers or accept taps. */
@Composable
internal fun ReviewCardStack(
    card: CardWithLesson,
    targets: List<PronunciationTarget>,
    remaining: Int,
    revealed: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = recallStrings()

    Box(modifier.padding(top = RecallSpacing.lg, bottom = RecallSpacing.lg + RecallSpacing.md)) {
        if (remaining > 2) Surface(
            Modifier.matchParentSize().offset(y = RecallSpacing.md).graphicsLayer { scaleX = .90f; rotationZ = -2f }.clearAndSetSemantics {},
            shape = RecallRadii.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 2.dp,
        ) {}
        if (remaining > 1) Surface(
            Modifier.matchParentSize().offset(y = RecallSpacing.xs).graphicsLayer { scaleX = .96f; rotationZ = 1.4f }.clearAndSetSemantics {},
            shape = RecallRadii.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 3.dp,
        ) {}
        // One active reading surface: no retained outgoing text/layout or metadata.
        // Reset scroll for the next card; retain it when pronunciation data arrives.
        key(card.id) {
            val displayed = card
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RecallRadii.extraLarge,
                color = MaterialTheme.colorScheme.surfaceElevated,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 4.dp,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(RecallSpacing.lg),
                    verticalArrangement = Arrangement.Center,
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(s(R.string.ui_question), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(if (displayed.type == "cloze") s(R.string.ui_fill_blank) else s(R.string.ui_active_recall), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted)
                        }
                        PronounceableStudyText(displayed.front, "front", targets, displayed.learningLanguage,
                            style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.md))
                        displayed.hint?.takeIf { it.isNotBlank() }?.let {
                            Text(s(R.string.ui_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.lg))
                            BidiAwareText(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.xxs))
                        }
                    }
                    item {
                        AnimatedVisibility(revealed, enter = fadeIn(tween(RecallMotion.quick)), exit = fadeOut(tween(RecallMotion.quick))) {
                            Column(Modifier.padding(top = RecallSpacing.lg)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(s(R.string.ui_answer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.success, modifier = Modifier.padding(top = RecallSpacing.lg))
                                PronounceableStudyText(displayed.back, "back", targets, displayed.learningLanguage,
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.sm))
                            }
                        }
                    }
                }
            }
        }
    }
}
