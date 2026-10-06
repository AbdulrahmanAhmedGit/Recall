package com.example.myapplication4.ui

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

@Composable fun SubjectSheet(initial: SubjectEntity? = null, save: (Pair<String, String>) -> Unit, dismiss: () -> Unit) { var name by remember { mutableStateOf(initial?.name.orEmpty()) }; var accent by remember { mutableStateOf(initial?.accent ?: "blue") }; RecallSheet(dismiss) { SheetTitle(if (initial == null) "New subject" else "Edit subject", "Keep it broad: Chemistry, Physics, German…"); RecallInput(name, { name = it }, "Subject name"); Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.md), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { listOf("blue", "purple", "orange", "teal").forEach { value -> FilterChip(accent == value, { accent = value }, { Text(value.replaceFirstChar { it.titlecase() }) }) } }; RecallPrimaryButton(if (initial == null) "Create subject" else "Save changes", onClick = { save(name to accent) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) } }

@Composable fun NameSheet(title: String, label: String, initial: String = "", save: (String) -> Unit, dismiss: () -> Unit) { var name by remember { mutableStateOf(initial) }; RecallSheet(dismiss) { SheetTitle(title); RecallInput(name, { name = it }, label); RecallPrimaryButton("Save", onClick = { save(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.md)) } }

@Composable fun AddChoiceSheet(lesson: () -> Unit, chapter: () -> Unit, dismiss: () -> Unit) { RecallSheet(dismiss) { SheetTitle("Add to subject"); ListItem(headlineContent = { Text("Lesson") }, supportingContent = { Text("Start studying immediately. No chapter required.") }, leadingContent = { Icon(Icons.Outlined.Book, null) }, modifier = Modifier.fillMaxWidth().clickable { lesson() }); ListItem(headlineContent = { Text("Chapter") }, supportingContent = { Text("Group related lessons together.") }, leadingContent = { Icon(Icons.Outlined.FolderOpen, null) }, modifier = Modifier.fillMaxWidth().clickable { chapter() }) } }

@Composable fun LessonSheet(initial: LessonEntity? = null, initialTags: List<String> = emptyList(), chapters: List<ChapterEntity>, selectedChapter: String?, save: (String, String?, List<String>, String?) -> Unit, dismiss: () -> Unit) { var title by remember { mutableStateOf(initial?.title.orEmpty()) }; var summary by remember { mutableStateOf(initial?.summary.orEmpty()) }; var tags by remember { mutableStateOf(initialTags.joinToString(", ")) }; var chapter by remember { mutableStateOf(selectedChapter) }; RecallSheet(dismiss) { Column(Modifier.verticalScroll(rememberScrollState())) { SheetTitle(if (initial == null) "New lesson" else "Edit lesson", "A lesson can live directly in its subject."); RecallInput(title, { title = it }, "Lesson title"); Spacer(Modifier.height(RecallSpacing.sm)); RecallInput(summary, { summary = it }, "Summary (optional)", singleLine = false, minLines = 2); Spacer(Modifier.height(RecallSpacing.sm)); RecallInput(tags, { tags = it }, "Tags, separated by commas"); if(chapters.isNotEmpty()) { Text("Chapter · optional", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = RecallSpacing.md)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { FilterChip(chapter == null, { chapter = null }, { Text("None") }); chapters.forEach { item -> FilterChip(chapter == item.id, { chapter = item.id }, { BidiAwareText(item.name, style = MaterialTheme.typography.labelMedium) }) } } }; RecallPrimaryButton(if (initial == null) "Create lesson" else "Save changes", onClick = { save(title, summary.takeIf { it.isNotBlank() }, tags.split(',').map { it.trim() }.filter { it.isNotBlank() }, chapter) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg)) } } }

@Composable
fun CardEditorSheet(
    initial: CardEntity? = null,
    initialTargets: List<PronunciationTarget> = emptyList(),
    learningLanguage: String? = null,
    save: (String, String, String?, String, List<PronunciationTarget>) -> Unit,
    dismiss: () -> Unit,
) {
    var front by remember { mutableStateOf(initial?.front.orEmpty()) }
    var back by remember { mutableStateOf(initial?.back.orEmpty()) }
    var hint by remember { mutableStateOf(initial?.hint.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: "qa") }
    var targets by remember { mutableStateOf(initialTargets) }
    val validation = remember(front, back, targets, learningLanguage) { PronunciationResolver.validate(front, back, targets, learningLanguage) }
    RecallSheet(dismiss, expanded = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(if (initial == null) "Add card" else "Edit card", "One clear, testable idea works best.")
            Row(horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
                FilterChip(type == "qa", { type = "qa" }, { Text("Question / answer") })
                FilterChip(type == "cloze", { type = "cloze" }, { Text("Cloze") })
            }
            RecallInput(front, { front = it }, if (type == "qa") "Question" else "Text with {{answer}}", singleLine = false, minLines = 3)
            Spacer(Modifier.height(RecallSpacing.sm))
            RecallInput(back, { back = it }, "Answer", singleLine = false, minLines = 3)
            Text("Science: [[chem:2H₂ + O₂ → 2H₂O]]  ·  [[math:V = IR]]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.xs))
            Spacer(Modifier.height(RecallSpacing.sm))
            RecallInput(hint, { hint = it }, "Hint (optional)")
            PronunciationEditor(front, back, targets, learningLanguage) { targets = it }
            if (validation.skipped > 0) Text(
                validation.skipped.toString() + " targets no longer match. Edit or remove them before saving.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
            RecallPrimaryButton(
                if (initial == null) "Add card" else "Save changes",
                onClick = { save(front, back, hint.takeIf { it.isNotBlank() }, type, validation.targets) },
                enabled = front.isNotBlank() && back.isNotBlank() && validation.skipped == 0,
                modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg),
            )
        }
    }
}

@Composable fun ScheduleSheet(initial: ScheduleBlockEntity? = null, save: (String, String, Int, Int, Set<Int>) -> Unit, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }; var type by remember { mutableStateOf(initial?.type ?: "quiet") }; var start by remember { mutableStateOf(initial?.startMinute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "19:00") }; var end by remember { mutableStateOf(initial?.endMinute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "21:00") }; var days by remember { mutableStateOf(initial?.days?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: (1..7).toSet()) }
    RecallSheet(dismiss) { Column(Modifier.verticalScroll(rememberScrollState())) { SheetTitle("Study schedule", "Quiet time wins whenever schedules overlap."); Row(horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { FilterChip(type == "quiet", { type = "quiet" }, { Text("Quiet time") }); FilterChip(type == "window", { type = "window" }, { Text("Study window") }) }; RecallInput(name, { name = it }, "Name", Modifier.padding(top = RecallSpacing.sm)); Row(Modifier.fillMaxWidth().padding(top = RecallSpacing.sm), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) { RecallInput(start, { start = it }, "Start", Modifier.weight(1f)); RecallInput(end, { end = it }, "End", Modifier.weight(1f)) }; Text("Days", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = RecallSpacing.md)); listOf(listOf("M","T","W","T"), listOf("F","S","S")).forEachIndexed { row, labels -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { labels.forEachIndexed { index, label -> val day = if(row == 0) index + 1 else index + 5; FilterChip(day in days, { days = if(day in days) days-day else days+day }, { Text(label) }, modifier = Modifier.weight(1f)) } } }; RecallPrimaryButton("Save schedule", onClick = { save(name, type, parseMinutes(start), parseMinutes(end), days) }, enabled = name.isNotBlank() && days.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg)) } }
}

@Composable fun <T> SettingsOptionsSheet(title: String, options: List<Pair<T, String>>, selected: T, choose: (T) -> Unit, dismiss: () -> Unit) {
    RecallSheet(dismiss) {
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

@Composable fun PauseRemindersSheet(save: (Long?) -> Unit, dismiss: () -> Unit) {
    var customHours by remember { mutableFloatStateOf(8f) }
    val now = System.currentTimeMillis()
    fun at(hour: Int, tomorrow: Boolean): Long = Calendar.getInstance().apply {
        if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (!tomorrow && timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    RecallSheet(dismiss) {
        SheetTitle("Pause reminders", "Due dates stay unchanged while notifications are paused.")
        listOf(
            "Resume now" to null,
            "For 1 hour" to now + 60 * 60_000L,
            "For 2 hours" to now + 2 * 60 * 60_000L,
            "For 4 hours" to now + 4 * 60 * 60_000L,
            "Until this evening" to at(19, false),
            "Until tomorrow morning" to at(8, true),
        ).forEach { (label, value) ->
            ListItem(headlineContent = { Text(label) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceElevated), modifier = Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable { save(value) })
        }
        Text("Custom · " + customHours.toInt() + " hours", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = RecallSpacing.sm))
        Slider(customHours, { customHours = it }, valueRange = 1f..72f, steps = 70)
        RecallPrimaryButton("Pause for " + customHours.toInt() + " hours", onClick = { save(now + customHours.toLong() * 60 * 60_000L) }, modifier = Modifier.fillMaxWidth())
    }
}

private fun parseMinutes(value: String): Int { val parts=value.split(":");return (parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0,23)?:0)*60+(parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0,59)?:0) }
