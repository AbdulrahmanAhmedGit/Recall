package com.example.myapplication4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.R
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.CardWithLesson
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.RecallPrimaryButton
import com.example.myapplication4.ui.design.*
import com.example.myapplication4.util.recallStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewFocusSheet(vm: RecallViewModel, initialSubject: String? = null, initialChapter: String? = null,
    initialLesson: String? = null, review: (List<CardWithLesson>) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    var subjectId by rememberSaveable { mutableStateOf(initialSubject) }
    var chapterId by rememberSaveable { mutableStateOf(initialChapter) }
    var lessonId by rememberSaveable { mutableStateOf(initialLesson) }
    var practice by rememberSaveable { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }
    val loading by vm.reviewLoading.collectAsStateWithLifecycle()
    val chapters by remember(subjectId) { vm.chapters(subjectId.orEmpty()) }.collectAsStateWithLifecycle(emptyList())
    val available = remember(lessons, subjectId, chapterId) { lessons.filter { it.subjectId == subjectId && (chapterId == null || it.chapterId == chapterId) } }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = RecallSpacing.ml).padding(bottom = RecallSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
            Text(s(R.string.focus_title), style = MaterialTheme.typography.titleLarge)
            Text(s(R.string.focus_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
            FocusSelector(s(R.string.ui_subject), subjects.firstOrNull { it.id == subjectId }?.name ?: s(R.string.focus_choose_subject),
                subjects.map { it.id to it.name }) { subjectId = it; chapterId = null; lessonId = null; empty = false }
            if (subjectId != null) {
                FocusSelector(s(R.string.ui_chapter), chapters.firstOrNull { it.id == chapterId }?.name ?: s(R.string.focus_all_chapters),
                    listOf(null to s(R.string.focus_all_chapters)) + chapters.map { it.id to it.name }) { chapterId = it; lessonId = null; empty = false }
                FocusSelector(s(R.string.ui_lesson), available.firstOrNull { it.id == lessonId }?.title ?: s(R.string.focus_all_lessons),
                    listOf(null to s(R.string.focus_all_lessons)) + available.map { it.id to it.title }) { lessonId = it; empty = false }
            }
            FilterChip(!practice, { practice = false; empty = false }, { Text(s(R.string.focus_due)) })
            FilterChip(practice, { practice = true; empty = false }, { Text(s(R.string.focus_practice)) })
            Text(s(if (practice) R.string.focus_practice_hint else R.string.focus_due_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
            if (empty) Text(s(R.string.focus_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
            RecallPrimaryButton(s(if (loading) R.string.ui_loading else R.string.focus_start), Icons.Outlined.AutoStories, {
                subjectId?.let { id -> vm.reviewFocus(ReviewFocus(id, chapterId, lessonId), practice) { cards ->
                    if (cards.isEmpty()) empty = true else { dismiss(); review(cards) }
                } }
            }, Modifier.fillMaxWidth(), enabled = !loading && subjects.any { it.id == subjectId } && (lessonId == null || available.any { it.id == lessonId }))
        }
    }
}

@Composable
private fun FocusSelector(label: String, value: String, options: List<Pair<String?, String>>, select: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted)
        Box {
            TextButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = RecallSizes.touch)) {
                BidiAwareText(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = RecallSizes.buttonHeight * 5)) {
                options.forEach { (id, name) -> DropdownMenuItem({ BidiAwareText(name) }, { expanded = false; select(id) }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonPauseSheet(lessonId: String, vm: RecallViewModel, dismiss: () -> Unit) {
    ReviewPauseSheet(ReviewPauseScope.LESSON, lessonId, vm, dismiss)
}

@Composable
internal fun pauseTitle(scope: ReviewPauseScope): String {
    val s = recallStrings()
    return s(when (scope) { ReviewPauseScope.SUBJECT -> R.string.pause_subject; ReviewPauseScope.CHAPTER -> R.string.pause_chapter; ReviewPauseScope.LESSON -> R.string.lesson_pause_title })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPauseSheet(scope: ReviewPauseScope, targetId: String, vm: RecallViewModel, dismiss: () -> Unit) {
    val s = recallStrings()
    val rules by vm.reviewPauses.collectAsStateWithLifecycle()
    val pause = rules.firstOrNull { it.key == (scope to targetId) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    fun save(first: LocalDate, last: LocalDate) { vm.setReviewPause(scope, targetId, reviewPauseDays(scope, targetId, first, last, ZoneId.systemDefault())); dismiss() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = RecallSpacing.ml).padding(bottom = RecallSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
            Text(pauseTitle(scope), style = MaterialTheme.typography.titleLarge)
            Text(s(R.string.pause_scope_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
            Text(s(R.string.pause_overlap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
            pause?.let { Text(s(R.string.lesson_pause_dates, s.date(it.startAt), s.date(it.endAt - 1)), style = MaterialTheme.typography.bodySmall) }
            TextButton({ save(today, today) }, Modifier.fillMaxWidth().heightIn(min = RecallSizes.touch)) { Text(s(R.string.lesson_pause_today)) }
            TextButton({ save(today, today.plusDays(6)) }, Modifier.fillMaxWidth().heightIn(min = RecallSizes.touch)) { Text(s(R.string.lesson_pause_week)) }
            TextButton({ custom = true }, Modifier.fillMaxWidth().heightIn(min = RecallSizes.touch)) { Text(s(R.string.lesson_pause_custom)) }
            if (pause != null) TextButton({ vm.setReviewPause(scope, targetId, null); dismiss() }, Modifier.fillMaxWidth().heightIn(min = RecallSizes.touch)) { Text(s(R.string.lesson_pause_resume)) }
        }
    }
    if (custom) {
        val earliest = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selectable = remember(earliest) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= earliest
            override fun isSelectableYear(year: Int) = year >= today.year
        } }
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = earliest, selectableDates = selectable)
        Dialog(onDismissRequest = { custom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    DateRangePicker(state, Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(RecallSpacing.md), horizontalArrangement = Arrangement.End) {
                        TextButton({ custom = false }) { Text(s(R.string.ui_cancel)) }
                        TextButton({
                            val first = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                            val last = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                            save(first, last)
                        }, enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null) { Text(s(R.string.ui_save)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPausePicker(vm: RecallViewModel, dismiss: () -> Unit) {
    val s = recallStrings()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    var subjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var chapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var lessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingDates by rememberSaveable { mutableStateOf(false) }
    val chapters by remember(subjectId) { vm.chapters(subjectId.orEmpty()) }.collectAsStateWithLifecycle(emptyList())
    val available = remember(lessons, subjectId, chapterId) { lessons.filter { it.subjectId == subjectId && (chapterId == null || it.chapterId == chapterId) } }
    if (choosingDates) {
        val scope = when { lessonId != null -> ReviewPauseScope.LESSON; chapterId != null -> ReviewPauseScope.CHAPTER; else -> ReviewPauseScope.SUBJECT }
        ReviewPauseSheet(scope, lessonId ?: chapterId ?: subjectId!!, vm, dismiss)
    } else ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = RecallSpacing.ml).padding(bottom = RecallSpacing.lg), verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
            Text(s(R.string.pause_title), style = MaterialTheme.typography.titleLarge)
            Text(s(R.string.pause_picker_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
            FocusSelector(s(R.string.ui_subject), subjects.firstOrNull { it.id == subjectId }?.name ?: s(R.string.focus_choose_subject), subjects.map { it.id to it.name }) { subjectId = it; chapterId = null; lessonId = null }
            if (subjectId != null) {
                FocusSelector(s(R.string.ui_chapter), chapters.firstOrNull { it.id == chapterId }?.name ?: s(R.string.focus_all_chapters), listOf(null to s(R.string.focus_all_chapters)) + chapters.map { it.id to it.name }) { chapterId = it; lessonId = null }
                FocusSelector(s(R.string.ui_lesson), available.firstOrNull { it.id == lessonId }?.title ?: s(R.string.focus_all_lessons), listOf(null to s(R.string.focus_all_lessons)) + available.map { it.id to it.title }) { lessonId = it }
            }
            RecallPrimaryButton(s(R.string.pause_choose_dates), onClick = { choosingDates = true }, modifier = Modifier.fillMaxWidth(), enabled = subjects.any { it.id == subjectId } && (lessonId == null || available.any { it.id == lessonId }))
        }
    }
}
