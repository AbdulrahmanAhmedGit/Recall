package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
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
import com.example.myapplication4.domain.ReviewPauseScope
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.text.DateFormat
import java.util.Date

@Composable
fun SubjectScreen(subjectId: String, vm: RecallViewModel, back: () -> Unit, openLesson: (String) -> Unit, review: (List<CardWithLesson>) -> Unit, import: () -> Unit) {
    val s = recallStrings()

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
    var focus by rememberSaveable { mutableStateOf(false) }
    var focusChapter by rememberSaveable { mutableStateOf<String?>(null) }
    var pauseSubject by rememberSaveable { mutableStateOf(false) }
    var pauseChapter by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenFrame {
        RecallTopBar(subject?.name.orEmpty(), s.count(R.plurals.ui_lessons, lessons.size) + " · " + s.count(R.plurals.ui_cards, lessons.sumOf { it.total }) + " · " + s.count(R.plurals.ui_due, lessons.sumOf { it.due }), back, { Row { RecallIconButton(Icons.Outlined.Add, s(R.string.ui_add), { addKind = "choice" }); Box { RecallIconButton(Icons.Outlined.MoreVert, s(R.string.ui_subject_actions)) { subjectMenu = true }; DropdownMenu(subjectMenu, { subjectMenu = false }) { DropdownMenuItem({ Text(s(R.string.ui_edit_subject)) }, { subjectMenu = false; editingSubject = true }); DropdownMenuItem({ Text(s(R.string.ui_delete_subject), color = MaterialTheme.colorScheme.error) }, { subjectMenu = false; deleteSubject = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
            FilterChip(!resourcesTab, { resourcesTab = false }, { Text(s(R.string.ui_lessons)) })
            FilterChip(resourcesTab, { resourcesTab = true }, { Text(s(R.string.ui_resources)) })
            TextButton(import) { Text(s(R.string.ui_import_cards)) }
        }
        if(resourcesTab) SubjectResources(subjectId, vm)
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = RecallSpacing.xl)) {
        item { if (lessons.sumOf { it.total } > 0) RecallPrimaryButton(if (lessons.sumOf { it.due } > 0) s.count(R.plurals.ui_review_cards, lessons.sumOf { it.due }) else s(R.string.ui_review_anyway), Icons.Outlined.AutoStories, { vm.reviewSubject(subjectId, review) }, Modifier.fillMaxWidth()) }
        item { Row(Modifier.fillMaxWidth()) { TextButton({ focusChapter = null; focus = true }, Modifier.weight(1f)) { Text(s(R.string.focus_title)) }; TextButton({ pauseSubject = true }, Modifier.weight(1f)) { Text(s(R.string.pause_subject)) } } }
        val direct = lessonsByChapter[null].orEmpty()
        if (direct.isNotEmpty()) { item { SectionHeader(s(R.string.ui_lessons)) }; items(direct, key = { it.id }) { LessonRow(it, false, modifier = recallItemMotion()) { openLesson(it.id) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
        chapters.forEach { chapter -> val child = lessonsByChapter[chapter.id].orEmpty(); item(key = chapter.id) { ChapterHeader(chapter, child.size, { editingChapter = chapter }, { addKind = "lesson:${chapter.id}" }, { deleteChapter = chapter }, { focusChapter = chapter.id; focus = true }, { pauseChapter = chapter.id }) }; if (child.isEmpty()) item(key = "empty-${chapter.id}") { Text(s(R.string.ui_chapter_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(bottom = RecallSpacing.sm)) } else items(child, key = { it.id }) { LessonRow(it, false, modifier = recallItemMotion()) { openLesson(it.id) } } }
        if (lessons.isEmpty() && chapters.isEmpty()) item { RecallEmptyState(Icons.Outlined.Book, s(R.string.ui_start_lesson), s(R.string.ui_optional_hierarchy), s(R.string.ui_add_lesson)) { addKind = "lesson" } }
    } }
    when { addKind == "choice" -> AddChoiceSheet({ addKind = "lesson" }, { addKind = "chapter" }) { addKind = null }; addKind == "chapter" -> NameSheet(s(R.string.ui_new_chapter), s(R.string.ui_chapter_name), save = { vm.addChapter(subjectId, it); addKind = null }) { addKind = null }; addKind?.startsWith("lesson") == true -> LessonSheet(chapters = chapters, selectedChapter = addKind?.substringAfter(':', "")?.takeIf { it.isNotEmpty() }, save = { title, summary, tags, chapter -> vm.addLesson(subjectId, chapter, title, summary, tags); addKind = null }) { addKind = null } }
    if(editingSubject && subject != null) SubjectSheet(initial = subject, save = { vm.editSubject(subject.copy(name = it.first, accent = it.second)); editingSubject = false }) { editingSubject = false }
    editingChapter?.let { chapter -> NameSheet(s(R.string.ui_edit_chapter), s(R.string.ui_name), initial = chapter.name, save = { vm.editChapter(chapter.copy(name = it)); editingChapter = null }) { editingChapter = null } }
    if (deleteSubject) ConfirmDeleteDialog(s(R.string.ui_delete_named, subject?.name ?: s(R.string.ui_subject)), s(R.string.ui_delete_subject_body, s.number(lessons.size), s.number(lessons.sumOf { it.total })), s(R.string.ui_delete_subject), { deleteSubject = false }) { vm.deleteSubject(subjectId, back) }
    deleteChapter?.let { chapter -> val count = lessons.count { it.chapterId == chapter.id }; ConfirmDeleteDialog(s(R.string.ui_delete_named, chapter.name), s(R.string.ui_delete_chapter_body, s.number(count)), s(R.string.ui_delete_chapter), { deleteChapter = null }) { vm.deleteChapter(chapter.id); deleteChapter = null } }
    if (focus) ReviewFocusSheet(vm, initialSubject = subjectId, initialChapter = focusChapter, review = review) { focus = false }
    if (pauseSubject) ReviewPauseSheet(ReviewPauseScope.SUBJECT, subjectId, vm) { pauseSubject = false }
    pauseChapter?.let { id -> ReviewPauseSheet(ReviewPauseScope.CHAPTER, id, vm) { pauseChapter = null } }
}

@Composable private fun ChapterHeader(chapter: ChapterEntity, lessonCount: Int, edit: () -> Unit, add: () -> Unit, delete: () -> Unit, focus: () -> Unit, pause: () -> Unit) {
    val s = recallStrings()
 var menu by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().padding(top = RecallSpacing.lg, bottom = RecallSpacing.sm), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { BidiAwareText(chapter.name, style = MaterialTheme.typography.titleLarge); Text(s.count(R.plurals.ui_lessons, lessonCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }; TextButton(add) { Text(s(R.string.ui_add_lesson)) }; Box { RecallIconButton(Icons.Outlined.MoreVert, s(R.string.ui_chapter_actions)) { menu = true }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text(s(R.string.focus_title)) }, { menu = false; focus() }); DropdownMenuItem({ Text(s(R.string.pause_chapter)) }, { menu = false; pause() }); DropdownMenuItem({ Text(s(R.string.ui_edit_chapter)) }, { menu = false; edit() }); DropdownMenuItem({ Text(s(R.string.ui_delete_chapter), color = MaterialTheme.colorScheme.error) }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } } }

private enum class LessonTab { Cards, Summary, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(lessonId: String, lesson: LessonOverview?, vm: RecallViewModel, back: () -> Unit, review: (List<CardWithLesson>) -> Unit, import: () -> Unit) {
    val s = recallStrings()

    val cards by remember(lessonId) { vm.cards(lessonId) }.collectAsStateWithLifecycle(emptyList())
    val tags by remember(lessonId) { vm.tags(lessonId) }.collectAsStateWithLifecycle(emptyList())
    var editingLesson by remember { mutableStateOf<LessonEntity?>(null) }
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }
    var focus by rememberSaveable { mutableStateOf(false) }
    var pauseEditor by rememberSaveable { mutableStateOf(false) }
    val pauseRules by vm.reviewPauses.collectAsStateWithLifecycle()
    val pause = pauseRules.firstOrNull { when (it.scope) {
        ReviewPauseScope.SUBJECT -> it.targetId == lesson?.subjectId
        ReviewPauseScope.CHAPTER -> it.targetId == lesson?.chapterId
        ReviewPauseScope.LESSON -> it.targetId == lessonId
    } }
    val chapters by remember(lesson?.subjectId) { vm.chapters(lesson?.subjectId.orEmpty()) }.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(LessonTab.Cards) }; var addingCard by remember { mutableStateOf(false) }; var delete by remember { mutableStateOf<CardEntity?>(null) }; var lessonMenu by remember { mutableStateOf(false) }; var deleteLesson by remember { mutableStateOf(false) }
    ScreenFrame { Column(Modifier.fillMaxSize()) {
        RecallTopBar(lesson?.title ?: s(R.string.ui_lesson), listOfNotNull(lesson?.subjectName, lesson?.chapterName).joinToString(" / "), back, { Box { RecallIconButton(Icons.Outlined.MoreVert, s(R.string.ui_lesson_actions)) { lessonMenu = true }; DropdownMenu(lessonMenu, { lessonMenu = false }) { DropdownMenuItem({ Text(s(R.string.ui_edit_lesson)) }, { lessonMenu = false; vm.loadLesson(lessonId) { editingLesson = it } }); DropdownMenuItem({ Text(s(R.string.ui_import_cards)) }, { lessonMenu = false; import() }); DropdownMenuItem({ Text(s(R.string.ui_delete_lesson), color = MaterialTheme.colorScheme.error) }, { lessonMenu = false; deleteLesson = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } })
        if (tags.isNotEmpty()) LessonTagChips(tags, edit = { editingTag = it }, remove = { vm.removeLessonTag(lessonId, it.id) })
        Spacer(Modifier.height(RecallSpacing.md)); Row(Modifier.fillMaxWidth()) { StatItem(s(R.string.ui_cards), s.number(lesson?.total ?: cards.size), Modifier.weight(1f), lesson?.total ?: cards.size); StatItem(s(R.string.ui_due), s.number(lesson?.due ?: 0), Modifier.weight(1f), lesson?.due ?: 0); StatItem(s(R.string.ui_next), dueLabel(lesson?.nextDue, s), Modifier.weight(1f)) }
        RecallPrimaryButton(if ((lesson?.due ?: 0) > 0) s.count(R.plurals.ui_review_cards, lesson?.due ?: 0) else s(R.string.ui_review_anyway), Icons.Outlined.AutoStories, { vm.reviewLesson(lessonId, review) }, Modifier.fillMaxWidth().padding(top = RecallSpacing.ml))
        Row(Modifier.fillMaxWidth()) {
            TextButton({ focus = true }, Modifier.weight(1f)) { Text(s(R.string.focus_title)) }
            TextButton({ pauseEditor = true }, Modifier.weight(1f)) { Text(s(R.string.lesson_pause_title)) }
        }
        pause?.let { Text(s(R.string.lesson_pause_dates, s.date(it.startAt), s.date(it.endAt - 1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }
        PrimaryTabRow(tab.ordinal, modifier = Modifier.padding(top = RecallSpacing.lg)) { LessonTab.entries.forEach { item -> Tab(tab == item, { tab = item }, text = { Text(s(when (item) { LessonTab.Cards -> R.string.ui_cards; LessonTab.Summary -> R.string.ui_summary; LessonTab.History -> R.string.ui_history })) }) } }
        when (tab) {
            LessonTab.Cards -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = RecallSpacing.sm, bottom = RecallSpacing.lg)) { item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(s.count(R.plurals.ui_cards, cards.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.muted, modifier = Modifier.weight(1f)); TextButton(onClick = { addingCard = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(RecallSpacing.xxs)); Text(s(R.string.ui_add_card)) } } }; if (cards.isEmpty()) item { RecallEmptyState(Icons.Outlined.Style, s(R.string.ui_no_cards), s(R.string.ui_add_or_import), s(R.string.ui_add_card)) { addingCard = true } } else items(cards, key = { it.id }) { card -> CardRow(card, { editingCard = card }, { vm.setSuspended(card.id, !card.suspended) }, { vm.duplicateCard(card) }, { delete = card }, modifier = recallItemMotion()); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
            LessonTab.Summary -> Column(Modifier.recallArrival(tab).padding(vertical = RecallSpacing.lg)) { BidiAwareText(lesson?.summary ?: s(R.string.ui_no_summary), style = MaterialTheme.typography.bodyLarge, color = if (lesson?.summary == null) MaterialTheme.colorScheme.muted else MaterialTheme.colorScheme.onSurface); TextButton(onClick = import, modifier = Modifier.padding(top = RecallSpacing.md)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(RecallSpacing.xs)); Text(s(R.string.ui_import_cards)) } }
            LessonTab.History -> Column(Modifier.recallArrival(tab).padding(vertical = RecallSpacing.lg)) { Text(s(R.string.ui_last_review), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted); Text(lesson?.lastReviewed?.let { s.date(it, time = true) } ?: s(R.string.ui_not_reviewed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = RecallSpacing.xxs)); Spacer(Modifier.height(RecallSpacing.lg)); Text(s(R.string.insights_reviewed_once), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted); Text(s.number(lesson?.learned ?: 0), style = MaterialTheme.typography.titleLarge) }
        }
    } }
    editingLesson?.let { value -> LessonSheet(initial = value, initialTags = tags.map { it.name }, chapters = chapters, selectedChapter = value.chapterId, save = { title, summary, names, chapter -> vm.editLesson(value.id, title, summary, chapter, names); editingLesson = null }) { editingLesson = null } }
    editingCard?.let { value -> StoredCardEditor(value, lesson?.learningLanguage, vm) { editingCard = null } }
    editingTag?.let { value -> NameSheet(s(R.string.ui_rename_tag), s(R.string.ui_tag_name), initial = value.name, save = { vm.editTag(value.copy(name = it)); editingTag = null }) { editingTag = null } }
    if (addingCard) CardEditorSheet(learningLanguage = lesson?.learningLanguage, save = { front, backText, hint, type, targets -> vm.addCard(lessonId, front, backText, hint, type, targets); addingCard = false }) { addingCard = false }
    delete?.let { card -> AlertDialog(onDismissRequest = { delete = null }, icon = { Icon(Icons.Outlined.Delete, null) }, title = { Text(s(R.string.ui_delete_card_question)) }, text = { BidiAwareText(card.front, maxLines = 3) }, confirmButton = { TextButton(onClick = { vm.deleteCard(card.id); delete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(s(R.string.ui_delete)) } }, dismissButton = { TextButton(onClick = { delete = null }) { Text(s(R.string.ui_keep_card)) } }) }
    if (deleteLesson) ConfirmDeleteDialog(s(R.string.ui_delete_named, lesson?.title ?: s(R.string.ui_lesson)), s(R.string.ui_delete_lesson_body, s.number(cards.size)), s(R.string.ui_delete_lesson), { deleteLesson = false }) { vm.deleteLesson(lessonId, back) }
    if (focus && lesson != null) ReviewFocusSheet(vm, lesson.subjectId, lesson.chapterId, lessonId, review) { focus = false }
    if (pauseEditor) LessonPauseSheet(lessonId, vm) { pauseEditor = false }
}

@Composable private fun ConfirmDeleteDialog(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    val s = recallStrings()
 AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }, title = { BidiAwareText(title, style = MaterialTheme.typography.titleLarge) }, text = { BidiAwareText(message) }, confirmButton = { TextButton(confirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(action) } }, dismissButton = { TextButton(dismiss) { Text(s(R.string.ui_cancel)) } }) }

@Composable private fun StatItem(label: String, value: String, modifier: Modifier = Modifier, count: Int? = null) { Column(modifier) { if (count != null) AnimatedRecallCount(count) else Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) } }

@Composable private fun CardRow(card: CardEntity, edit: () -> Unit, suspend: () -> Unit, duplicate: () -> Unit, delete: () -> Unit, modifier: Modifier = Modifier) {
    val s = recallStrings()
 var menu by remember { mutableStateOf(false) }; Row(modifier.fillMaxWidth().padding(vertical = RecallSpacing.md), verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(if (card.type == "cloze") s(R.string.ui_cloze) else s(R.string.ui_qa), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); if (card.suspended) { Spacer(Modifier.width(RecallSpacing.xs)); Text(s(R.string.ui_suspended), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.warning) } }; BidiAwareText(card.front, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = RecallSpacing.xxs), maxLines = 3, overflow = TextOverflow.Ellipsis); BidiAwareText(card.back, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }; Box { RecallIconButton(Icons.Outlined.MoreVert, s(R.string.ui_card_actions)) { menu = true }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text(s(R.string.ui_edit_card)) }, { menu = false; edit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }); DropdownMenuItem({ Text(if (card.suspended) s(R.string.ui_resume) else s(R.string.ui_suspend)) }, { menu = false; suspend() }, leadingIcon = { Icon(if (card.suspended) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null) }); DropdownMenuItem({ Text(s(R.string.ui_duplicate)) }, { menu = false; duplicate() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }); DropdownMenuItem({ Text(s(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }) } } } }

private fun dueLabel(value: Long?, s: com.example.myapplication4.util.RecallStrings): String = when { value == null -> "—"; value <= System.currentTimeMillis() -> s(R.string.ui_now); value - System.currentTimeMillis() < 86_400_000L -> s(R.string.ui_today); else -> s(R.string.ui_later) }
