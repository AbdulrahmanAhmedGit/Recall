package com.example.myapplication4.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication4.R
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.design.*
import com.example.myapplication4.util.recallStrings
import java.text.NumberFormat

@Composable
fun ObservedRecallSection(history: RecallHistoryAnalysis) {
    val s = recallStrings()
    val sample = history.current
    val percent = remember(s.locale) { NumberFormat.getPercentInstance(s.locale).apply { maximumFractionDigits = 0 } }
    Column(Modifier.fillMaxWidth().testTag("observed-recall"), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        Text(s(R.string.insights_recall_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        BidiAwareText(s(R.string.insights_recall_period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        sample.rate?.let { rate ->
            AnimatedRecallNumber(rate, { percent.format(it) }, Modifier.testTag("observed-recall-percentage"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        } ?: BidiAwareText(s(when {
            sample.total > 0 -> R.string.insights_insufficient
            history.missingAuditCurrent > 0 -> R.string.insights_legacy_sample
            else -> R.string.insights_no_sample
        }), style = MaterialTheme.typography.bodyMedium)
        BidiAwareText(s(R.string.insights_sample, s.number(sample.successful), s.number(sample.total), s.number(sample.distinctCards)),
            modifier = Modifier.testTag("observed-recall-counts"), style = MaterialTheme.typography.bodyMedium)
        BidiAwareText(s(R.string.insights_recall_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        history.change?.let { change ->
            val number = NumberFormat.getNumberInstance(s.locale).apply { maximumFractionDigits = 1 }
            val difference = (if (change > 0) "+" else "") + number.format(change * 100)
            BidiAwareText(s(R.string.insights_change, difference), Modifier.testTag("observed-recall-change"), style = MaterialTheme.typography.bodySmall)
            BidiAwareText(s(R.string.insights_change_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        }
    }
}

@Composable
fun PredictedRecallSection(prediction: CurrentPredictedRecall) {
    val s = recallStrings()
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = RecallSizes.touch).testTag("predicted-recall-toggle")) {
        Text(s(R.string.insights_predicted_title))
    }
    RecallExpansion(expanded) { Column(Modifier.fillMaxWidth().testTag("predicted-recall"), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        val percent = remember(s.locale) { NumberFormat.getPercentInstance(s.locale).apply { maximumFractionDigits = 0 } }
        prediction.probability?.let { probability ->
            AnimatedRecallNumber(probability, { percent.format(it) }, Modifier.testTag("predicted-recall-percentage"),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        } ?: BidiAwareText(s(R.string.insights_predicted_empty), style = MaterialTheme.typography.bodyMedium)
        BidiAwareText(s(R.string.insights_predicted_coverage, s.number(prediction.eligibleCards), s.number(prediction.activeCards)),
            Modifier.testTag("predicted-recall-coverage"), style = MaterialTheme.typography.bodyMedium)
        if (prediction.eligibleCards in 1..9) BidiAwareText(s(R.string.insights_predicted_small),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        BidiAwareText(s(R.string.insights_predicted_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    } }
}

@Composable
fun MemoryMaturitySection(memory: MemoryCounts) {
    val s = recallStrings()
    Column(Modifier.fillMaxWidth().testTag("memory-maturity"), verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
        BidiAwareText(s(R.string.insights_memory_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
            listOf(R.string.insights_new to memory.new, R.string.ui_learning to memory.learning, R.string.insights_mature to memory.mature).forEach { (label, count) ->
                Column(Modifier.weight(1f)) {
                    AnimatedRecallCount(count, style = MaterialTheme.typography.titleLarge)
                    BidiAwareText(s(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
                }
            }
        }
        BidiAwareText(s(R.string.insights_longer_memory) + " · " + s.number(memory.mature) + " / " + s.number(memory.total),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.muted)
        val progress = animatedRecallProgress(if (memory.total == 0) 0f else memory.mature.toFloat() / memory.total)
        LinearProgressIndicator(progress = { progress.value },
            modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
fun InsightsExplanation(history: RecallHistoryAnalysis) {
    val s = recallStrings()
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = RecallSizes.touch).testTag("insights-explanation-toggle")) {
        Text(s(R.string.insights_details))
    }
    RecallExpansion(expanded) { Column(Modifier.fillMaxWidth().testTag("insights-explanation"), verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
        listOf(R.string.insights_recall_rules, R.string.insights_memory_rules, R.string.insights_attention_rules, R.string.insights_active_scope).forEach { resource ->
            BidiAwareText(s(resource), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        }
        BidiAwareText(s(R.string.insights_excluded, s.number(history.excludedCurrent), s.number(history.missingAuditCurrent)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    } }
}

@Composable
fun AttentionLessonRow(lesson: AttentionLesson, now: Long, onOpenLesson: () -> Unit) {
    val s = recallStrings()
    val card = lesson.card
    val evidence = lesson.group.example
    var expanded by rememberSaveable(card.cardId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm).testTag("attention-${lesson.group.lessonId}"),
        verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
        Column(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(onClick = onOpenLesson).padding(RecallSpacing.xs)) {
            BidiAwareText(card.lessonTitle, style = MaterialTheme.typography.titleMedium)
            BidiAwareText(card.subjectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
            BidiAwareText(s.count(R.plurals.insights_affected_cards, lesson.group.affectedCards), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = RecallSpacing.xs))
            BidiAwareText(card.front, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = RecallSpacing.xs))
            BidiAwareText(s(R.string.insights_failure_pattern, s.number(evidence.failures), s.number(evidence.attempts)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = RecallSizes.touch)) { Text(s(R.string.ui_history)) }
        RecallExpansion(expanded) { Column {
            val numbers = NumberFormat.getNumberInstance(s.locale).apply { maximumFractionDigits = 1 }
            fun finiteNumber(value: Double) = if (value.isFinite()) numbers.format(value) else s(R.string.ui_none)
            BidiAwareText(s(R.string.insights_card_context, finiteNumber(card.difficulty), s.number(card.lapses),
                finiteNumber(card.stability), s.number(card.scheduledDays)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
            BidiAwareText(s(R.string.insights_next_review, if (card.dueAt <= now) s(R.string.insights_due_now) else s.date(card.dueAt)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        } }
    }
}
