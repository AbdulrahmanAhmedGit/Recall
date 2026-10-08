package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag

@Composable internal fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth < 360.dp) RecallSizes.compactPagePadding else RecallSizes.pagePadding
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = padding).widthIn(max = RecallSizes.contentMaxWidth).align(Alignment.TopCenter), content = content)
    }
}

@Composable
fun TodayScreen(due: Int, lessons: List<LessonOverview>, vm: RecallViewModel, review: (List<CardWithLesson>) -> Unit, import: () -> Unit, calendar: () -> Unit = {}) {
    val s = recallStrings()

    val reviewLoading by vm.reviewLoading.collectAsStateWithLifecycle()
    val backlog = com.example.myapplication4.domain.BacklogPolicy.isBacklog(due)
    val dueLessons = remember(lessons) { lessons.filter { it.due > 0 } }
    val upcoming = remember(lessons) { lessons.filter { it.due == 0 && it.total > 0 }.take(3) }
    ScreenFrame {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = RecallSpacing.lg)) {
            item {
                Spacer(Modifier.height(RecallSpacing.lg))
                Text(s.date(System.currentTimeMillis()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s(R.string.ui_today), style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f).padding(top = RecallSpacing.xxs).semantics { heading() })
                    ReviewCalendarAction(vm, calendar)
                }
                Spacer(Modifier.height(RecallSpacing.xl))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.number(due), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    Text(s.resources.getQuantityString(R.plurals.ui_due_suffix, due), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 5.dp, start = RecallSpacing.xs))
                }
                Text(s(R.string.ui_today_counts, s.count(R.plurals.ui_lessons, dueLessons.size), s.count(R.plurals.ui_minutes, if (due == 0) 0 else maxOf(1, due / 4))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
                if (backlog) Text(s(R.string.phase3_backlog_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.sm))
                RecallPrimaryButton(if (reviewLoading) s(R.string.ui_loading) else if (backlog) s(R.string.phase3_small_session) else if (due > 0) s(R.string.ui_start_review) else s(R.string.ui_review_anyway), Icons.Outlined.AutoStories, { if (backlog) vm.reviewBatch(review) else vm.reviewDue(review) }, Modifier.fillMaxWidth().padding(top = RecallSpacing.lg), enabled = !reviewLoading)
                if (backlog) TextButton({ vm.reviewDue(review) }, enabled = !reviewLoading) { Text(s(R.string.phase3_all_due)) }
                SectionHeader(s(R.string.ui_due_lessons), if (dueLessons.isEmpty()) s(R.string.ui_import) else null, import)
            }
            if (dueLessons.isEmpty()) item { RecallEmptyState(Icons.Outlined.AutoStories, s(R.string.ui_caught_up_title), s(R.string.ui_due_empty)) }
            else items(dueLessons, key = { it.id }) { lesson -> LessonRow(lesson) { vm.reviewLesson(lesson.id, review) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            if (upcoming.isNotEmpty()) { item { SectionHeader(s(R.string.ui_upcoming)) }; items(upcoming, key = { "up-${it.id}" }) { LessonRow(it) { vm.reviewLesson(it.id, review) } } }
        }
    }
}

@Composable
fun LibraryScreen(subjects: List<SubjectEntity>, lessons: List<LessonOverview>, vm: RecallViewModel, openSubject: (String) -> Unit, import: () -> Unit) {
    val s = recallStrings()

    var adding by remember { mutableStateOf(false) }
    // Avoid a full lesson scan for each subject row in large libraries.
    val lessonsBySubject = remember(lessons) { lessons.groupBy { it.subjectId } }
    ScreenFrame {
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = RecallSizes.buttonHeight + RecallSpacing.lg)) {
            item { RecallTopBar(s(R.string.ui_library), s(R.string.ui_library_hint), action = { RecallIconButton(Icons.Outlined.FileDownload, s(R.string.ui_import_cards), import) }); SectionHeader(s(R.string.ui_subjects), s(R.string.ui_add)) { adding = true } }
            if (subjects.isEmpty()) item { RecallEmptyState(Icons.Outlined.LocalLibrary, s(R.string.ui_first_subject), s(R.string.ui_chapters_optional), s(R.string.ui_add_subject)) { adding = true } }
            else items(subjects, key = { it.id }) { subject -> val subjectLessons = lessonsBySubject[subject.id].orEmpty(); SubjectRow(subject, subjectLessons.size, subjectLessons.sumOf { it.total }, subjectLessons.sumOf { it.due }) { openSubject(subject.id) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }
        RecallPrimaryButton(s(R.string.ui_quick_add), Icons.Outlined.Add, { adding = true }, Modifier.align(Alignment.BottomEnd).padding(bottom = RecallSpacing.xs))
        }
    }
    if (adding) SubjectSheet(save = { vm.addSubject(it.first, it.second); adding = false }) { adding = false }
}

@Composable
fun InsightsScreen(vm: RecallViewModel, calendar: () -> Unit = {}, openLesson: (String) -> Unit = {}) {
    val s = recallStrings()

    val reviewed by vm.todayReviews.collectAsStateWithLifecycle()
    val activity by vm.studyActivity.collectAsStateWithLifecycle()
    val insights by vm.learningInsights.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = if (settings.language in com.example.myapplication4.util.RecallLocale.languages) {
        Locale.Builder().setLanguage(settings.language).apply {
            if (systemLocale.country.isNotEmpty()) setRegion(systemLocale.country)
        }.build()
    } else systemLocale
    ScreenFrame { LazyColumn(Modifier.fillMaxSize().testTag("insights-list"), contentPadding = PaddingValues(bottom = RecallSpacing.lg)) {
        item { RecallTopBar(s(R.string.ui_insights), s(R.string.ui_insights_hint), action = { ReviewCalendarAction(vm, calendar) }) }
        item(key = "activity") { BidiAwareText(s(R.string.insights_activity_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted); StudyActivityCard(activity, Modifier.padding(top = RecallSpacing.xs), locale) }
        item { SectionHeader(s(R.string.ui_today)); StatStrip(listOf(s(R.string.ui_reviewed) to s.number(reviewed), s(R.string.ui_study_time) to s(R.string.ui_estimated_minutes, s.number(reviewed / 4)), s(R.string.insights_review_target) to java.text.NumberFormat.getPercentInstance(s.locale).format(settings.desiredRetention))) }
        val summary = insights
        if (summary == null) item { Text(s(R.string.ui_loading), Modifier.padding(vertical = RecallSpacing.lg), color = MaterialTheme.colorScheme.muted) }
        else {
            item(key = "recall") { Spacer(Modifier.height(RecallSpacing.lg)); ObservedRecallSection(summary.history) }
            item(key = "memory") { SectionHeader(s(R.string.insights_memory_title)); MemoryMaturitySection(summary.memory); InsightsExplanation(summary.history); PredictedRecallSection(summary.predicted) }
            item(key = "attention-title") { SectionHeader(s(R.string.insights_attention_title)); BidiAwareText(s(if (summary.attention.isEmpty()) R.string.insights_attention_empty else R.string.insights_attention_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }
            items(summary.attention, key = { "attention-${it.group.lessonId}" }) { suggestion ->
                AttentionLessonRow(suggestion, summary.now) { openLesson(suggestion.group.lessonId) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    } }
}

@Composable private fun StatStrip(values: List<Pair<String, String>>) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) { values.forEach { (label, value) -> Column(Modifier.weight(1f)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) } } } }
