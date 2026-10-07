package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import androidx.compose.foundation.horizontalScroll
import com.example.myapplication4.ui.components.RecallPrimaryButton
import com.example.myapplication4.ui.design.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun RecallSheet(onDismiss: () -> Unit, expanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded), containerColor = MaterialTheme.colorScheme.surfaceElevated, contentColor = MaterialTheme.colorScheme.onSurface, shape = RecallRadii.extraLarge) { Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = RecallSizes.pagePadding).padding(bottom = RecallSpacing.lg), content = content) }
}

@Composable private fun SheetTitle(title: String, body: String? = null) { Text(title, style = MaterialTheme.typography.titleLarge); if(body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.xxs, bottom = RecallSpacing.md)) else Spacer(Modifier.height(RecallSpacing.md)) }

@Composable internal fun RecallInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, minLines: Int = 1) { OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines, shape = RecallRadii.medium, textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) }

@Composable fun SubjectSheet(initial: SubjectEntity? = null, save: (Pair<String, String>) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()
 var name by remember { mutableStateOf(initial?.name.orEmpty()) }; var accent by remember { mutableStateOf(initial?.accent ?: "blue") }; RecallSheet(dismiss) { SheetTitle(if (initial == null) s(R.string.ui_new_subject) else s(R.string.ui_edit_subject), s(R.string.ui_subject_hint)); RecallInput(name, { name = it }, s(R.string.ui_subject_name)); Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.md), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { listOf("blue", "purple", "orange", "teal").forEach { value -> FilterChip(accent == value, { accent = value }, { Text(s(when(value) { "purple" -> R.string.ui_purple; "orange" -> R.string.ui_orange; "teal" -> R.string.ui_teal; else -> R.string.ui_blue })) }) } }; RecallPrimaryButton(if (initial == null) s(R.string.ui_create_subject) else s(R.string.ui_save_changes), onClick = { save(name to accent) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) } }

@Composable fun NameSheet(title: String, label: String, initial: String = "", save: (String) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()
 var name by remember { mutableStateOf(initial) }; RecallSheet(dismiss) { SheetTitle(title); RecallInput(name, { name = it }, label); RecallPrimaryButton(s(R.string.ui_save), onClick = { save(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.md)) } }

@Composable fun AddChoiceSheet(lesson: () -> Unit, chapter: () -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()
 RecallSheet(dismiss) { SheetTitle(s(R.string.ui_add_to_subject)); ListItem(headlineContent = { Text(s(R.string.ui_lesson)) }, supportingContent = { Text(s(R.string.ui_lesson_no_chapter)) }, leadingContent = { Icon(Icons.Outlined.Book, null) }, modifier = Modifier.fillMaxWidth().clickable { lesson() }); ListItem(headlineContent = { Text(s(R.string.ui_chapter)) }, supportingContent = { Text(s(R.string.ui_chapter_hint)) }, leadingContent = { Icon(Icons.Outlined.FolderOpen, null) }, modifier = Modifier.fillMaxWidth().clickable { chapter() }) } }

@Composable fun LessonSheet(initial: LessonEntity? = null, initialTags: List<String> = emptyList(), chapters: List<ChapterEntity>, selectedChapter: String?, save: (String, String?, List<String>, String?) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()
 var title by remember { mutableStateOf(initial?.title.orEmpty()) }; var summary by remember { mutableStateOf(initial?.summary.orEmpty()) }; var tags by remember { mutableStateOf(initialTags.joinToString(", ")) }; var chapter by remember { mutableStateOf(selectedChapter) }; RecallSheet(dismiss) { Column(Modifier.verticalScroll(rememberScrollState())) { SheetTitle(if (initial == null) s(R.string.ui_new_lesson) else s(R.string.ui_edit_lesson), s(R.string.ui_lesson_direct)); RecallInput(title, { title = it }, s(R.string.ui_lesson_title)); Spacer(Modifier.height(RecallSpacing.sm)); RecallInput(summary, { summary = it }, s(R.string.ui_summary_optional), singleLine = false, minLines = 2); Spacer(Modifier.height(RecallSpacing.sm)); RecallInput(tags, { tags = it }, s(R.string.ui_tags_input)); if(chapters.isNotEmpty()) { Text(s(R.string.ui_chapter_optional), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = RecallSpacing.md)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { FilterChip(chapter == null, { chapter = null }, { Text(s(R.string.ui_none)) }); chapters.forEach { item -> FilterChip(chapter == item.id, { chapter = item.id }, { BidiAwareText(item.name, style = MaterialTheme.typography.labelMedium) }) } } }; RecallPrimaryButton(if (initial == null) s(R.string.ui_create_lesson) else s(R.string.ui_save_changes), onClick = { save(title, summary.takeIf { it.isNotBlank() }, tags.split(',', '،').map { it.trim() }.filter { it.isNotBlank() }, chapter) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg)) } } }

@Composable
fun CardEditorSheet(
    initial: CardEntity? = null,
    initialTargets: List<PronunciationTarget> = emptyList(),
    learningLanguage: String? = null,
    save: (String, String, String?, String, List<PronunciationTarget>) -> Unit,
    dismiss: () -> Unit,
) {
    val s = recallStrings()

    var front by remember { mutableStateOf(initial?.front.orEmpty()) }
    var back by remember { mutableStateOf(initial?.back.orEmpty()) }
    var hint by remember { mutableStateOf(initial?.hint.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: "qa") }
    var targets by remember { mutableStateOf(initialTargets) }
    val validation = remember(front, back, targets, learningLanguage) { PronunciationResolver.validate(front, back, targets, learningLanguage) }
    RecallSheet(dismiss, expanded = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(if (initial == null) s(R.string.ui_add_card) else s(R.string.ui_edit_card), s(R.string.ui_card_idea))
            Row(horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
                FilterChip(type == "qa", { type = "qa" }, { Text(s(R.string.ui_qa)) })
                FilterChip(type == "cloze", { type = "cloze" }, { Text(s(R.string.ui_cloze)) })
            }
            RecallInput(front, { front = it }, if (type == "qa") s(R.string.ui_question) else s(R.string.ui_cloze_input), singleLine = false, minLines = 3)
            Spacer(Modifier.height(RecallSpacing.sm))
            RecallInput(back, { back = it }, s(R.string.ui_answer), singleLine = false, minLines = 3)
            Text(s(R.string.ui_science_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.xs))
            Spacer(Modifier.height(RecallSpacing.sm))
            RecallInput(hint, { hint = it }, s(R.string.ui_hint_optional))
            PronunciationEditor(front, back, targets, learningLanguage) { targets = it }
            if (validation.skipped > 0) Text(
                s(R.string.ui_targets_mismatch, s.number(validation.skipped)),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
            RecallPrimaryButton(
                if (initial == null) s(R.string.ui_add_card) else s(R.string.ui_save_changes),
                onClick = { save(front, back, hint.takeIf { it.isNotBlank() }, type, validation.targets) },
                enabled = front.isNotBlank() && back.isNotBlank() && validation.skipped == 0,
                modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg),
            )
        }
    }
}

@Composable fun ScheduleSheet(initial: ScheduleBlockEntity? = null, save: (String, String, Int, Int, Set<Int>) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }; var type by remember { mutableStateOf(initial?.type ?: "quiet") }; var start by remember { mutableStateOf(initial?.startMinute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "19:00") }; var end by remember { mutableStateOf(initial?.endMinute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "21:00") }; var days by remember { mutableStateOf(initial?.days?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: (1..7).toSet()) }
    RecallSheet(dismiss) { Column(Modifier.verticalScroll(rememberScrollState())) { SheetTitle(s(R.string.ui_study_schedule), s(R.string.ui_quiet_overlap)); Row(horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { FilterChip(type == "quiet", { type = "quiet" }, { Text(s(R.string.ui_quiet_time)) }); FilterChip(type == "window", { type = "window" }, { Text(s(R.string.ui_study_window)) }) }; RecallInput(name, { name = it }, s(R.string.ui_name), Modifier.padding(top = RecallSpacing.sm)); Row(Modifier.fillMaxWidth().padding(top = RecallSpacing.sm), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) { RecallInput(start, { start = it }, s(R.string.ui_start), Modifier.weight(1f)); RecallInput(end, { end = it }, s(R.string.ui_end), Modifier.weight(1f)) }; Text(s(R.string.ui_days), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = RecallSpacing.md)); listOf((1..4).map(s::weekday), (5..7).map(s::weekday)).forEachIndexed { row, labels -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { labels.forEachIndexed { index, label -> val day = if(row == 0) index + 1 else index + 5; FilterChip(day in days, { days = if(day in days) days-day else days+day }, { Text(label) }, modifier = Modifier.weight(1f)) } } }; RecallPrimaryButton(s(R.string.ui_save_schedule), onClick = { save(name, type, parseMinutes(start), parseMinutes(end), days) }, enabled = name.isNotBlank() && days.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg)) } }
}

@Composable fun <T> SettingsOptionsSheet(title: String, options: List<Pair<T, String>>, selected: T, choose: (T) -> Unit, dismiss: () -> Unit) {
    RecallSheet(dismiss, expanded = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetTitle(title)
        options.forEach { (value, label) ->
            ListItem(
                headlineContent = { BidiAwareText(label, style = MaterialTheme.typography.bodyLarge) },
                trailingContent = { if (value == selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceElevated),
                modifier = Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable { choose(value) },
            )
        }
        }
    }
}

@Composable fun PauseRemindersSheet(save: (Long?) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()

    var customHours by remember { mutableFloatStateOf(8f) }
    val now = System.currentTimeMillis()
    fun at(hour: Int, tomorrow: Boolean): Long = Calendar.getInstance().apply {
        if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (!tomorrow && timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    RecallSheet(dismiss) {
        SheetTitle(s(R.string.ui_pause_reminders), s(R.string.ui_pause_hint))
        listOf(
            s(R.string.ui_resume_now) to null,
            s(R.string.ui_pause_one) to now + 60 * 60_000L,
            s(R.string.ui_pause_two) to now + 2 * 60 * 60_000L,
            s(R.string.ui_pause_four) to now + 4 * 60 * 60_000L,
            s(R.string.ui_pause_evening) to at(19, false),
            s(R.string.ui_pause_tomorrow) to at(8, true),
        ).forEach { (label, value) ->
            ListItem(headlineContent = { Text(label) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceElevated), modifier = Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable { save(value) })
        }
        Text(s(R.string.ui_custom_hours, s.number(customHours.toInt())), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = RecallSpacing.sm))
        Slider(customHours, { customHours = it }, valueRange = 1f..72f, steps = 70)
        RecallPrimaryButton(s(R.string.ui_pause_hours, s.number(customHours.toInt())), onClick = { save(now + customHours.toLong() * 60 * 60_000L) }, modifier = Modifier.fillMaxWidth())
    }
}

private fun parseMinutes(value: String): Int { val parts=value.split(":");return (parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0,23)?:0)*60+(parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0,59)?:0) }
