package com.example.myapplication4.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.res.Configuration
import com.example.myapplication4.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.text.DateFormat
import java.util.Date

@Composable
fun ReviewScreen(cards: List<CardWithLesson>, vm: RecallViewModel, done: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var skipped by rememberSaveable { mutableIntStateOf(0) }
    var ratingCounts by rememberSaveable { mutableStateOf(List(Rating.entries.size) { 0 }) }
    val counts = Rating.entries.associateWith { ratingCounts[it.ordinal] }
    val haptic = LocalHapticFeedback.current
    val reviewSettings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, reviewSettings.language) {
        if (reviewSettings.language in setOf("en", "ar")) {
            context.createConfigurationContext(Configuration(configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(reviewSettings.language))
            }).resources
        } else context.resources
    }
    if (cards.isEmpty()) { ScreenFrame { RecallTopBar("Review", onBack = done); RecallEmptyState(Icons.Outlined.CheckCircle, "Nothing due", "Your scheduled reviews are complete.", "Done", done) }; return }
    if (index >= cards.size) { ReviewComplete(counts.values.sum(), skipped, counts, resources, done); return }
    val card = cards[index]
    val reviewedAt = remember(card.id) { System.currentTimeMillis() }
    val cardStartedAt = remember(card.id) { reviewedAt }
    val previews = remember(card.id, reviewedAt, reviewSettings.desiredRetention) { vm.preview(card, reviewedAt, reviewSettings.desiredRetention) }
    val targets by remember(card.id) { vm.pronunciations(card.id) }.collectAsStateWithLifecycle(emptyList())
    DisposableEffect(card.id) { onDispose { vm.pronunciation.stop() } }
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val pagePadding = if (maxWidth < 360.dp) RecallSpacing.md else RecallSpacing.ml
        Column(Modifier.fillMaxSize().padding(horizontal = pagePadding).widthIn(max = RecallSizes.contentMaxWidth).align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().height(RecallSizes.touch), verticalAlignment = Alignment.CenterVertically) { RecallIconButton(Icons.Outlined.Close, "End review", done); Column(Modifier.weight(1f).padding(horizontal = RecallSpacing.xs)) { BidiAwareText(card.lessonTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1); BidiAwareText("${index + 1} of ${cards.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted) }; CircularProgressIndicator(progress = { (index + 1f) / cards.size }, modifier = Modifier.size(30.dp), strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant) }
            ReviewCardStack(card, targets, cards.size - index, revealed, Modifier.weight(1f).fillMaxWidth())
            TextButton(onClick = {
                // Skipping only advances this session: no review event or memory-state update.
                skipped++
                index++
                revealed = false
            }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = RecallSizes.touch)) {
                Text(resources.getString(R.string.review_skip))
            }
            if (!revealed) RecallPrimaryButton("Show answer", Icons.Outlined.Visibility, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); revealed = true }, Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = RecallSpacing.md))
            else RatingBar(previews) { result -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.rate(card, result, System.currentTimeMillis() - cardStartedAt); ratingCounts = ratingCounts.mapIndexed { i, count -> if (i == result.rating.ordinal) count + 1 else count }; index++; revealed = false }
        }
    }
}

@Composable private fun RatingBar(previews: Map<Rating, ScheduleResult>, rate: (ScheduleResult) -> Unit) { Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = RecallSpacing.md)) { BidiAwareText("How well did you remember?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(bottom = RecallSpacing.xs)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { Rating.entries.forEach { rating -> val result = previews.getValue(rating); RatingChoice(rating, formatReviewInterval(result.intervalMillis), Modifier.weight(1f)) { rate(result) } } } } }

@Composable private fun RatingChoice(rating: Rating, interval: String, modifier: Modifier, click: () -> Unit) { val tint = when(rating) { Rating.AGAIN -> MaterialTheme.colorScheme.error; Rating.HARD -> MaterialTheme.colorScheme.warning; Rating.GOOD -> MaterialTheme.colorScheme.primary; Rating.EASY -> MaterialTheme.colorScheme.success }; Surface(onClick = click, modifier = modifier.heightIn(min = 68.dp), color = MaterialTheme.colorScheme.surfaceInteractive, contentColor = MaterialTheme.colorScheme.onSurface, shape = RecallRadii.medium) { Column(Modifier.padding(vertical = RecallSpacing.sm, horizontal = RecallSpacing.xxs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(rating.name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelLarge, color = tint); BidiAwareText(interval, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted) } } }

@Composable
private fun ReviewComplete(total: Int, skipped: Int, counts: Map<Rating, Int>, resources: android.content.res.Resources, done: () -> Unit) {
    ScreenFrame {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceSelected), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text("Review complete", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = RecallSpacing.lg))
            Text(resources.getQuantityString(R.plurals.review_answered_count, total, total), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.muted)
            if (skipped > 0) Text(resources.getString(R.string.review_skipped_count, skipped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
            Spacer(Modifier.height(RecallSpacing.xl))
            Rating.entries.forEach { rating ->
                Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.xs)) {
                    Text(rating.name.lowercase().replaceFirstChar { it.titlecase() }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${counts[rating] ?: 0}", style = MaterialTheme.typography.titleMedium)
                }
            }
            RecallPrimaryButton("Done", onClick = done, modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.xl))
        }
    }
}

@Composable
fun ImportScreen(vm: RecallViewModel, targetSubjectId: String? = null, targetLessonId: String? = null, back: () -> Unit) {
    DisposableEffect(Unit) { onDispose { vm.pronunciation.stop() } }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(android.content.ClipboardManager::class.java) }
    val haptic = LocalHapticFeedback.current
    var selectedSubjectId by rememberSaveable(targetSubjectId) { mutableStateOf(targetSubjectId) }
    var destinationPicker by remember { mutableStateOf(false) }
    var raw by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedLesson = lessons.firstOrNull { it.id == targetLessonId }
    if(destinationPicker) SettingsOptionsSheet("Import destination", listOf<String?>(null).map { it to "Use subject from JSON" } + subjects.map { it.id to it.name }, selectedSubjectId, { selectedSubjectId = it; destinationPicker = false }) { destinationPicker = false }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read file")
        }.fold(
            onSuccess = { text ->
                if (text.length > 2_000_000) notice = "That file is too large. Recall accepts JSON files up to 2 MB."
                else { raw = text; result = null; notice = "JSON file loaded · ${text.length} characters" }
            },
            onFailure = { notice = "Recall could not read that file. Choose a local .json or text file." },
        )
    }
    fun pasteFromClipboard() {
        val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (pasted.isBlank()) notice = "The clipboard does not contain JSON text."
        else { raw = pasted; result = null; notice = "Pasted ${pasted.length} characters" }
    }
    ScreenFrame {
        Column(Modifier.fillMaxSize()) {
            RecallTopBar("Import from AI", "Portable Recall JSON works with any AI provider.", back)
            TextButton({ destinationPicker = true }, enabled = targetSubjectId == null) { Text("Destination: " + (selectedSubject?.name ?: "Use subject from JSON")) }
            if(selectedLesson != null) Text("Append to " + selectedLesson.title, style = MaterialTheme.typography.labelLarge)
            if (result !is ImportResult.Success) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(::pasteFromClipboard) { Icon(Icons.Outlined.ContentPaste, null); Spacer(Modifier.width(RecallSpacing.xxs)); Text("Paste") }
                    TextButton({ openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(RecallSpacing.xxs)); Text("Open file") }
                    Spacer(Modifier.weight(1f))
                    if (raw.isNotEmpty()) Text("${raw.length} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted)
                }
                OutlinedTextField(
                    raw,
                    { raw = it; result = null; notice = null },
                    Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Recall JSON") },
                    placeholder = { Text("Paste the complete response, including the first { and final }") },
                    shape = RecallRadii.medium,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                notice?.let { Text(it, color = MaterialTheme.colorScheme.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = RecallSpacing.xs)) }
                if (result is ImportResult.Failure) Text((result as ImportResult.Failure).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = RecallSpacing.xs))
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = RecallSpacing.md), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
                    OutlinedButton({ clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Recall AI prompt", selectedSubject?.let { subjectAiPrompt(it.name, selectedLesson?.title) } ?: AI_PROMPT)) }, Modifier.weight(1f).heightIn(min = RecallSizes.buttonHeight), shape = RecallRadii.medium) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(RecallSpacing.xs)); Text("AI prompt") }
                    RecallPrimaryButton("Preview", Icons.AutoMirrored.Outlined.ArrowForward, { result = RecallImportParser.parse(raw, selectedLesson?.learningLanguage) }, Modifier.weight(1f), raw.isNotBlank())
                }
            } else ImportPreview((result as ImportResult.Success).draft, { result = null }) { draft -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.importDraft(draft, back, selectedSubjectId, targetLessonId) }
        }
    }
}

@Composable internal fun ColumnScope.ImportPreview(initial: ImportDraft, editSource: () -> Unit, import: (ImportDraft) -> Unit) {
    var cards by remember(initial) { mutableStateOf(initial.cards) }
    var editing by remember { mutableStateOf<Int?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(cards.count { it.included && !it.duplicate }.toString() + " cards ready", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(editSource) { Text("Edit JSON") }
    }
    Text("Check each question before importing. You can edit or exclude any card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    if (initial.pronunciationWarnings > 0) Text(initial.pronunciationWarnings.toString() + " pronunciation targets were skipped because they could not be matched safely.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.warning)
    if (cards.any { it.pronunciationTargets.isNotEmpty() }) Text(androidx.compose.ui.res.stringResource(com.example.myapplication4.R.string.pronunciation_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = RecallSpacing.md)) {
        itemsIndexed(cards, key = { index, _ -> index }) { index, card ->
            Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalAlignment = Alignment.Top) {
                Checkbox(card.included && !card.duplicate, { checked -> cards = cards.toMutableList().also { it[index] = card.copy(included = checked) } }, enabled = !card.duplicate)
                Column(Modifier.weight(1f)) {
                    PronounceableStudyText(card.front, "front", card.pronunciationTargets, initial.learningLanguage, style = MaterialTheme.typography.titleSmall)
                    PronounceableStudyText(card.back, "back", card.pronunciationTargets, initial.learningLanguage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, maxLines = 3)
                    if(card.duplicate) Text("Duplicate · excluded", color = MaterialTheme.colorScheme.warning)
                }
                RecallIconButton(Icons.Outlined.Edit, "Edit import card") { editing = index }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    editing?.let { index -> val card = cards[index]
        CardEditorSheet(initial = CardEntity(lessonId = "", front = card.front, back = card.back, type = card.type, hint = card.hint), initialTargets = card.pronunciationTargets, learningLanguage = initial.learningLanguage, save = { front, back, hint, type, targets ->
            val changed = card.copy(front = front, back = back, hint = hint, type = type, duplicate = false, pronunciationTargets = targets)
            cards = cards.toMutableList().also { it[index] = changed }
            editing = null
        }) { editing = null }
    }
    RecallPrimaryButton("Import cards", Icons.Outlined.FileDownload, { import(initial.copy(cards = cards)) }, Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = RecallSpacing.md), cards.any { it.included && !it.duplicate })
}

private enum class SettingsPanel { Retention, NewCards, Pause, Appearance, Language, Scheduler, SpeechRate }

@Composable
fun SettingsScreen(vm: RecallViewModel, openInfo: () -> Unit) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editSchedule by remember { mutableStateOf<ScheduleBlockEntity?>(null) }
    var add by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<SettingsPanel?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var schedulerTaps by rememberSaveable { mutableIntStateOf(0) }
    val archiveBusy by vm.archiveBusy.collectAsStateWithLifecycle()
    val archiveStatus by vm.archiveStatus.collectAsStateWithLifecycle()
    var pendingArchive by remember { mutableStateOf<android.net.Uri?>(null) }
    val fullExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::exportAllData) }
    val fullRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingArchive = uri }
    fun schedulerTap() {
        if (!settings.debugMode) {
            schedulerTaps++
            if (schedulerTaps >= 6) {
                vm.setDebugMode(true); schedulerTaps = 0; panel = null; message = "Debug mode enabled."
                return
            }
        }
        panel = SettingsPanel.Scheduler
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setRemindersEnabled(granted)
        if (!granted) message = "Notification permission was not granted."
    }
    fun enableReminders(enabled: Boolean) {
        if (!enabled) vm.setRemindersEnabled(false)
        else if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) vm.setRemindersEnabled(true)
        else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup { result -> result.fold(
            onSuccess = { json -> runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } ?: error("Unable to open file") }.fold({ message = "Backup exported." }, { message = "Could not write the backup." }) },
            onFailure = { message = "Could not create the backup." },
        ) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingBackup = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            .also { if (it == null) message = "Could not read that file." }
    }
    ScreenFrame {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
            item {
                RecallTopBar("Settings", "Study on your terms.")
                val infoResources = recallInfoResources(settings.language)
                SettingRow(Icons.Outlined.Info, infoResources.getString(R.string.info_title), infoResources.getString(R.string.info_subtitle), openInfo)
                message?.let { Surface(color = MaterialTheme.colorScheme.surfaceSelected, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RecallRadii.medium, modifier = Modifier.fillMaxWidth().padding(bottom = RecallSpacing.sm)) { Text(it, Modifier.padding(RecallSpacing.sm), style = MaterialTheme.typography.bodySmall) } }
                SettingsSection("Study")
                SettingRow(Icons.Outlined.TrackChanges, "Desired retention", (settings.desiredRetention * 100).toInt().toString() + "% · " + if(settings.desiredRetention >= .93) "longer memory" else "balanced") { panel = SettingsPanel.Retention }
                SettingRow(Icons.Outlined.Style, "New cards per review", if(settings.newCardLimit == 0) "Reviews only" else "Up to " + settings.newCardLimit + " new cards") { panel = SettingsPanel.NewCards }
                SettingsSection("Study schedule", "Add") { add = true }
                Text("Quiet times always override study windows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(bottom = RecallSpacing.sm))
            }
            itemsIndexed(schedules, key = { _, it -> it.id }) { _, schedule -> ScheduleRow(schedule, { editSchedule = schedule }) { vm.deleteSchedule(schedule.id) } }
            item {
                SettingsSection("Notifications")
                ToggleSettingRow(Icons.Outlined.NotificationsNone, "Review reminders", "Only outside quiet time", settings.remindersEnabled, ::enableReminders)
                ToggleSettingRow(Icons.Outlined.Schedule, "Study-window reminder", "Notify near the beginning of a study window", settings.studyWindowReminder, vm::setStudyWindowReminder)
                SettingRow(Icons.Outlined.Snooze, "Pause reminders", settings.pausedUntil?.let { "Paused until " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Not paused") { panel = SettingsPanel.Pause }
                BackgroundReminderSettings { enableReminders(true) }
                SettingsSection("Appearance & language")
                SettingRow(Icons.Outlined.Contrast, "Appearance", settings.themeMode.replaceFirstChar { it.titlecase() }) { panel = SettingsPanel.Appearance }
                ToggleSettingRow(Icons.Outlined.Palette, "Dynamic color", "Use the device palette on Android 12+", settings.dynamicColor, vm::setDynamicColor, enabled = Build.VERSION.SDK_INT >= 31)
                SettingRow(Icons.Outlined.Language, "Language and direction", when(settings.language) { "ar" -> "العربية"; "en" -> "English"; else -> "System" }) { panel = SettingsPanel.Language }
                SettingsSection(androidx.compose.ui.res.stringResource(com.example.myapplication4.R.string.pronunciation))
                SettingRow(Icons.Outlined.RecordVoiceOver, androidx.compose.ui.res.stringResource(com.example.myapplication4.R.string.speech_rate), when(settings.speechRate) { .8f -> "Slow"; 1.2f -> "Fast"; else -> "Normal" }) { panel = SettingsPanel.SpeechRate }
                SettingRow(Icons.Outlined.Language, "Android voices", "Manage installed offline voices") { vm.pronunciation.manageVoices(context) }
                SettingsSection("Data")
                archiveStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = RecallSpacing.sm)) }
                if (archiveBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                SettingRow(Icons.Outlined.Archive, "Export all data", "Portable ZIP with cards, progress, notes, PDFs and photos") { if (!archiveBusy) fullExport.launch("Recall-all-data-" + System.currentTimeMillis() + ".zip") }
                SettingRow(Icons.Outlined.Restore, "Restore full backup", "Validate and merge a ZIP including its materials") { if (!archiveBusy) fullRestore.launch(arrayOf("application/zip", "application/octet-stream")) }
                SettingRow(Icons.Outlined.FileUpload, "Export data only", "JSON backup; attached file contents are not included") { export.launch("Recall-backup-" + System.currentTimeMillis() + ".json") }
                SettingRow(Icons.Outlined.FileDownload, "Import backup", "Merge safely; existing records are kept") { openBackup.launch(arrayOf("application/json", "text/plain")) }
                SettingsSection("Advanced")
                SettingRow(Icons.Outlined.Memory, "Scheduler information", "Adaptive scheduling and memory state") { schedulerTap() }
                if (settings.debugMode) ReminderDebugPanel(vm) { message = it }
            }
        }
    }
    editSchedule?.let { value -> ScheduleSheet(initial = value, save = { name, type, start, end, days -> vm.editSchedule(value.copy(name = name, type = type, startMinute = start, endMinute = end, days = days.sorted().joinToString(","))); editSchedule = null }) { editSchedule = null } }
    if (add) ScheduleSheet(save = { n,t,s,e,d -> vm.saveSchedule(n,t,s,e,d); add=false }) { add=false }
    when (panel) {
        SettingsPanel.SpeechRate -> SettingsOptionsSheet("Speech rate", listOf(.8f to "Slow", 1f to "Normal", 1.2f to "Fast"), settings.speechRate, { vm.setSpeechRate(it); panel = null }) { panel = null }
        SettingsPanel.Retention -> SettingsOptionsSheet("Desired retention", listOf(.85 to "85% · fewer reviews", .90 to "90% · balanced", .93 to "93% · stronger", .95 to "95% · most reviews"), settings.desiredRetention, { vm.setRetention(it); panel = null }) { panel = null }
        SettingsPanel.NewCards -> SettingsOptionsSheet("New cards per review", listOf(0 to "0 · reviews only", 10 to "10 cards", 20 to "20 cards", 30 to "30 cards", 50 to "50 cards"), settings.newCardLimit, { vm.setNewCardLimit(it); panel = null }) { panel = null }
        SettingsPanel.Appearance -> SettingsOptionsSheet("Appearance", listOf("system" to "System", "light" to "Light", "dark" to "Dark"), settings.themeMode, { vm.setThemeMode(it); panel = null }) { panel = null }
        SettingsPanel.Language -> SettingsOptionsSheet("Language and direction", listOf("system" to "System", "en" to "English", "ar" to "العربية"), settings.language, { vm.setLanguage(it); panel = null }) { panel = null }
        SettingsPanel.Pause -> PauseRemindersSheet({ vm.pauseReminders(it); panel = null }) { panel = null }
        SettingsPanel.Scheduler -> AlertDialog(onDismissRequest = { panel = null }, icon = { Icon(Icons.Outlined.Memory, null) }, title = { Column { Text("Scheduler information", modifier = Modifier.clickable { schedulerTap() }); if (!settings.debugMode) Text("Tap the title " + (6 - schedulerTaps).coerceAtLeast(0) + " more times for debug mode.", style = MaterialTheme.typography.bodySmall) } }, text = { Text("Recall tracks each card’s stability, difficulty, elapsed time, lapses, and rating. Desired retention changes future intervals without moving existing due dates.") }, confirmButton = { TextButton({ panel = null }) { Text("Done") } })
        null -> Unit
    }
    pendingArchive?.let { uri ->
        AlertDialog(onDismissRequest = { pendingArchive = null }, title = { Text("Restore all data?") },
            text = { Text("The archive and every attachment will be validated. Existing records are kept. Settings will be restored from this backup. Materials are copied into Recall for offline access.") },
            confirmButton = { TextButton({ pendingArchive = null; vm.restoreAllData(uri) }) { Text("Restore") } },
            dismissButton = { TextButton({ pendingArchive = null }) { Text("Cancel") } })
    }
    pendingBackup?.let { raw -> AlertDialog(onDismissRequest = { pendingBackup = null }, icon = { Icon(Icons.Outlined.Restore, null) }, title = { Text("Restore this backup?") }, text = { Text("The backup will be validated and merged. Existing records with the same IDs remain unchanged.") }, confirmButton = { TextButton({ pendingBackup = null; vm.importBackup(raw) { message = it } }) { Text("Restore") } }, dismissButton = { TextButton({ pendingBackup = null }) { Text("Cancel") } }) }
}

@Composable private fun SettingsSection(title: String, action: String? = null, click: () -> Unit = {}) = SectionHeader(title, action, click)
@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(onClick = click).padding(vertical = RecallSpacing.sm, horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(RecallSizes.icon), tint = MaterialTheme.colorScheme.muted); Spacer(Modifier.width(RecallSpacing.md)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }; Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.outline) } }
@Composable private fun ToggleSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) { Row(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(enabled = enabled) { change(!checked) }.padding(vertical = RecallSpacing.xs, horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(RecallSizes.icon), tint = if(enabled) MaterialTheme.colorScheme.muted else MaterialTheme.colorScheme.outline); Spacer(Modifier.width(RecallSpacing.md)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, color = if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.muted); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }; Switch(checked, change, enabled = enabled) } }
@Composable private fun ScheduleRow(block: ScheduleBlockEntity, edit: () -> Unit, delete: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalAlignment = Alignment.CenterVertically) { val quiet = block.type == "quiet"; Box(Modifier.size(42.dp).clip(RecallRadii.medium).background(if(quiet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer), contentAlignment=Alignment.Center){Icon(if(quiet)Icons.Outlined.DoNotDisturbOn else Icons.Outlined.Schedule,null,Modifier.size(20.dp), tint = if(quiet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)};Spacer(Modifier.width(RecallSpacing.sm));Column(Modifier.weight(1f)){BidiAwareText(block.name,style=MaterialTheme.typography.titleSmall);Text("${daysLabel(block.days)} · ${clock(block.startMinute)}–${clock(block.endMinute)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.muted)};RecallIconButton(Icons.Outlined.Edit,"Edit schedule",edit);RecallIconButton(Icons.Outlined.Delete,"Delete ${block.name}",delete)} }
private fun daysLabel(days:String)=if(days=="1,2,3,4,5,6,7")"Daily"else days.split(',').joinToString(" · "){listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")[it.toInt()-1]};private fun clock(minutes:Int)="%02d:%02d".format(minutes/60,minutes%60)
