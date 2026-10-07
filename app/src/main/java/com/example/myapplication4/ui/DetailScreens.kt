package com.example.myapplication4.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.text.DateFormat
import java.util.Date

@Composable
fun SubjectScreen(subjectId: String, vm: RecallViewModel, back: () -> Unit, openLesson: (String) -> Unit, review: (List<CardWithLesson>) -> Unit, import: () -> Unit) {
    val subject = vm.subjects.collectAsStateWithLifecycle().value.firstOrNull { it.id == subjectId }
    val allLessons by vm.lessons.collectAsStateWithLifecycle()
    val lessons = remember(allLessons, subjectId) { allLessons.filter { it.subjectId == subjectId } }
    val lessonsByChapter = remember(lessons) { lessons.groupBy { it.chapterId } }
    val chapters by remember(subjectId) { vm.chapters(subjectId) }.collectAsStateWithLifecycle(emptyList())
    var addKind by remember { mutableStateOf<String?>(null) }
    var editingSubject by remember { mutableStateOf(false) }
    var editingChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var resourcesTab by rememberSaveable { mutableStateOf(false) }
    var subjectMenu by remember { mutableStateOf(false) }
    var deleteSubject by remember { mutableStateOf(false) }
    var deleteChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    ScreenFrame {
        RecallTopBar(subject?.name.orEmpty(), "${lessons.size} lessons · ${lessons.sumOf { it.total }} cards · ${lessons.sumOf { it.due }} due", back, { Row { RecallIconButton(Icons.Outlined.Add, "Add", { addKind = "choice" }); Box { RecallIconButton(Icons.Outlined.MoreVert, "Subject actions") { subjectMenu = true }; DropdownMenu(subjectMenu, { subjectMenu = false }) { DropdownMenuItem({ Text("Edit subject") }, { subjectMenu = false; editingSubject = true }); DropdownMenuItem({ Text("Delete subject", color = MaterialTheme.colorScheme.error) }, { subjectMenu = false; deleteSubject = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
            FilterChip(!resourcesTab, { resourcesTab = false }, { Text("Lessons") })
            FilterChip(resourcesTab, { resourcesTab = true }, { Text("Resources") })
            TextButton(import) { Text("Import cards") }
        }
        if(resourcesTab) SubjectResources(subjectId, vm)
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = RecallSpacing.xl)) {
        item { if (lessons.sumOf { it.total } > 0) RecallPrimaryButton(if (lessons.sumOf { it.due } > 0) "Review ${lessons.sumOf { it.due }} cards" else "Review anyway", Icons.Outlined.AutoStories, { vm.reviewSubject(subjectId, review) }, Modifier.fillMaxWidth()) }
        val direct = lessonsByChapter[null].orEmpty()
        if (direct.isNotEmpty()) { item { SectionHeader("Lessons") }; items(direct, key = { it.id }) { LessonRow(it, false) { openLesson(it.id) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
        chapters.forEach { chapter -> val child = lessonsByChapter[chapter.id].orEmpty(); item(key = chapter.id) { ChapterHeader(chapter, child.size, { editingChapter = chapter }, { addKind = "lesson:${chapter.id}" }, { deleteChapter = chapter }) }; if (child.isEmpty()) item(key = "empty-${chapter.id}") { Text("No lessons in this chapter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(bottom = RecallSpacing.sm)) } else items(child, key = { it.id }) { LessonRow(it, false) { openLesson(it.id) } } }
        if (lessons.isEmpty() && chapters.isEmpty()) item { RecallEmptyState(Icons.Outlined.Book, "Start with a lesson", "Create it directly or organize it in an optional chapter.", "Add lesson") { addKind = "lesson" } }
    } }
    when { addKind == "choice" -> AddChoiceSheet({ addKind = "lesson" }, { addKind = "chapter" }) { addKind = null }; addKind == "chapter" -> NameSheet("New chapter", "Chapter name", save = { vm.addChapter(subjectId, it); addKind = null }) { addKind = null }; addKind?.startsWith("lesson") == true -> LessonSheet(chapters = chapters, selectedChapter = addKind?.substringAfter(':', "")?.takeIf { it.isNotEmpty() }, save = { title, summary, tags, chapter -> vm.addLesson(subjectId, chapter, title, summary, tags); addKind = null }) { addKind = null } }
    if(editingSubject && subject != null) SubjectSheet(initial = subject, save = { vm.editSubject(subject.copy(name = it.first, accent = it.second)); editingSubject = false }) { editingSubject = false }
    editingChapter?.let { chapter -> NameSheet("Edit chapter", "Name", initial = chapter.name, save = { vm.editChapter(chapter.copy(name = it)); editingChapter = null }) { editingChapter = null } }
    if (deleteSubject) ConfirmDeleteDialog("Delete ${subject?.name ?: "subject"}?", "This permanently deletes ${lessons.size} lessons and ${lessons.sumOf { it.total }} cards, including their review history. Subject notes and file references are also removed; original files are kept.", "Delete subject", { deleteSubject = false }) { vm.deleteSubject(subjectId, back) }
    deleteChapter?.let { chapter -> val count = lessons.count { it.chapterId == chapter.id }; ConfirmDeleteDialog("Delete ${chapter.name}?", "$count ${if (count == 1) "lesson" else "lessons"} will be kept and moved directly under the subject.", "Delete chapter", { deleteChapter = null }) { vm.deleteChapter(chapter.id); deleteChapter = null } }
}

@Composable private fun ChapterHeader(chapter: ChapterEntity, lessonCount: Int, edit: () -> Unit, add: () -> Unit, delete: () -> Unit) { var menu by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().padding(top = RecallSpacing.lg, bottom = RecallSpacing.sm), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { BidiAwareText(chapter.name, style = MaterialTheme.typography.titleLarge); Text("$lessonCount ${if(lessonCount == 1) "lesson" else "lessons"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }; TextButton(add) { Text("Add lesson") }; Box { RecallIconButton(Icons.Outlined.MoreVert, "Chapter actions") { menu = true }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Edit chapter") }, { menu = false; edit() }); DropdownMenuItem({ Text("Delete chapter", color = MaterialTheme.colorScheme.error) }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } } }

private enum class LessonTab { Cards, Summary, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(lessonId: String, lesson: LessonOverview?, vm: RecallViewModel, back: () -> Unit, review: (List<CardWithLesson>) -> Unit, import: () -> Unit) {
    val cards by remember(lessonId) { vm.cards(lessonId) }.collectAsStateWithLifecycle(emptyList())
    val tags by remember(lessonId) { vm.tags(lessonId) }.collectAsStateWithLifecycle(emptyList())
    var editingLesson by remember { mutableStateOf<LessonEntity?>(null) }
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }
    val chapters by remember(lesson?.subjectId) { vm.chapters(lesson?.subjectId.orEmpty()) }.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(LessonTab.Cards) }; var addingCard by remember { mutableStateOf(false) }; var delete by remember { mutableStateOf<CardEntity?>(null) }; var lessonMenu by remember { mutableStateOf(false) }; var deleteLesson by remember { mutableStateOf(false) }
    ScreenFrame { Column(Modifier.fillMaxSize()) {
        RecallTopBar(lesson?.title ?: "Lesson", listOfNotNull(lesson?.subjectName, lesson?.chapterName).joinToString(" / "), back, { Box { RecallIconButton(Icons.Outlined.MoreVert, "More lesson actions") { lessonMenu = true }; DropdownMenu(lessonMenu, { lessonMenu = false }) { DropdownMenuItem({ Text("Edit lesson") }, { lessonMenu = false; vm.loadLesson(lessonId) { editingLesson = it } }); DropdownMenuItem({ Text("Import cards") }, { lessonMenu = false; import() }); DropdownMenuItem({ Text("Delete lesson", color = MaterialTheme.colorScheme.error) }, { lessonMenu = false; deleteLesson = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } })
        if (tags.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { tags.take(4).forEach { tag -> InputChip(false, { editingTag = tag }, { BidiAwareText("#${tag.name}", style = MaterialTheme.typography.labelMedium) }, trailingIcon = { IconButton({ vm.removeLessonTag(lessonId, tag.id) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Remove tag", Modifier.size(16.dp)) } }) } }
        Spacer(Modifier.height(RecallSpacing.md)); Row(Modifier.fillMaxWidth()) { StatItem("Cards", "${lesson?.total ?: cards.size}", Modifier.weight(1f)); StatItem("Due", "${lesson?.due ?: 0}", Modifier.weight(1f)); StatItem("Next", dueLabel(lesson?.nextDue), Modifier.weight(1f)) }
        RecallPrimaryButton(if ((lesson?.due ?: 0) > 0) "Review ${lesson?.due} cards" else "Review anyway", Icons.Outlined.AutoStories, { vm.reviewLesson(lessonId, review) }, Modifier.fillMaxWidth().padding(top = RecallSpacing.ml))
        PrimaryTabRow(tab.ordinal, modifier = Modifier.padding(top = RecallSpacing.lg)) { LessonTab.entries.forEach { item -> Tab(tab == item, { tab = item }, text = { Text(item.name) }) } }
        when (tab) {
            LessonTab.Cards -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = RecallSpacing.sm, bottom = RecallSpacing.lg)) { item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("${cards.size} cards", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.muted, modifier = Modifier.weight(1f)); TextButton(onClick = { addingCard = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(RecallSpacing.xxs)); Text("Add card") } } }; if (cards.isEmpty()) item { RecallEmptyState(Icons.Outlined.Style, "No cards yet", "Add one manually or import from AI.", "Add card") { addingCard = true } } else items(cards, key = { it.id }) { card -> CardRow(card, { editingCard = card }, { vm.setSuspended(card.id, !card.suspended) }, { vm.duplicateCard(card) }, { delete = card }); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
            LessonTab.Summary -> Column(Modifier.padding(vertical = RecallSpacing.lg)) { BidiAwareText(lesson?.summary ?: "No summary yet.", style = MaterialTheme.typography.bodyLarge, color = if (lesson?.summary == null) MaterialTheme.colorScheme.muted else MaterialTheme.colorScheme.onSurface); TextButton(onClick = import, modifier = Modifier.padding(top = RecallSpacing.md)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(RecallSpacing.xs)); Text("Import cards") } }
            LessonTab.History -> Column(Modifier.padding(vertical = RecallSpacing.lg)) { Text("Last review", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted); Text(lesson?.lastReviewed?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Not reviewed yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = RecallSpacing.xxs)); Spacer(Modifier.height(RecallSpacing.lg)); Text("Learned cards", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted); Text("${lesson?.learned ?: 0}", style = MaterialTheme.typography.titleLarge) }
        }
    } }
    editingLesson?.let { value -> LessonSheet(initial = value, initialTags = tags.map { it.name }, chapters = chapters, selectedChapter = value.chapterId, save = { title, summary, names, chapter -> vm.editLesson(value.id, title, summary, chapter, names); editingLesson = null }) { editingLesson = null } }
    editingCard?.let { value -> StoredCardEditor(value, lesson?.learningLanguage, vm) { editingCard = null } }
    editingTag?.let { value -> NameSheet("Rename tag everywhere", "Tag name", initial = value.name, save = { vm.editTag(value.copy(name = it)); editingTag = null }) { editingTag = null } }
    if (addingCard) CardEditorSheet(learningLanguage = lesson?.learningLanguage, save = { front, backText, hint, type, targets -> vm.addCard(lessonId, front, backText, hint, type, targets); addingCard = false }) { addingCard = false }
    delete?.let { card -> AlertDialog(onDismissRequest = { delete = null }, icon = { Icon(Icons.Outlined.Delete, null) }, title = { Text("Delete this card?") }, text = { BidiAwareText(card.front, maxLines = 3) }, confirmButton = { TextButton(onClick = { vm.deleteCard(card.id); delete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Keep card") } }) }
    if (deleteLesson) ConfirmDeleteDialog("Delete ${lesson?.title ?: "lesson"}?", "This permanently deletes ${cards.size} cards and their review history.", "Delete lesson", { deleteLesson = false }) { vm.deleteLesson(lessonId, back) }
}

@Composable private fun ConfirmDeleteDialog(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) { AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }, title = { BidiAwareText(title, style = MaterialTheme.typography.titleLarge) }, text = { BidiAwareText(message) }, confirmButton = { TextButton(confirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(action) } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } }) }

@Composable private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) { Column(modifier) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) } }

@Composable private fun CardRow(card: CardEntity, edit: () -> Unit, suspend: () -> Unit, duplicate: () -> Unit, delete: () -> Unit) { var menu by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.md), verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(if (card.type == "cloze") "CLOZE" else "Q&A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); if (card.suspended) { Spacer(Modifier.width(RecallSpacing.xs)); Text("SUSPENDED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.warning) } }; BidiAwareText(card.front, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = RecallSpacing.xxs), maxLines = 3, overflow = TextOverflow.Ellipsis); BidiAwareText(card.back, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }; Box { RecallIconButton(Icons.Outlined.MoreVert, "Card actions") { menu = true }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Edit card") }, { menu = false; edit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }); DropdownMenuItem({ Text(if (card.suspended) "Resume" else "Suspend") }, { menu = false; suspend() }, leadingIcon = { Icon(if (card.suspended) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null) }); DropdownMenuItem({ Text("Duplicate") }, { menu = false; duplicate() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }); DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } } }

private fun dueLabel(value: Long?): String = when { value == null -> "—"; value <= System.currentTimeMillis() -> "Now"; value - System.currentTimeMillis() < 86_400_000L -> "Today"; else -> "Later" }
