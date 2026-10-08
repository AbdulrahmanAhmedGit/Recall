package com.example.myapplication4.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.res.Configuration
import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import com.example.myapplication4.util.importError
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(cards: List<CardWithLesson>, vm: RecallViewModel, done: () -> Unit) {
    val s = recallStrings()
    DisposableEffect(cards) { vm.activeReviewCards = cards; onDispose { } }

    val progress by vm.reviewProgress.collectAsStateWithLifecycle()
    val index = progress.index
    val revealed = progress.revealed
    val skipped = progress.skipped
    val saving = progress.saving
    val counts = Rating.entries.associateWith { progress.counts[it.ordinal] }
    val haptic = LocalHapticFeedback.current
    var lastCompleted by remember { mutableIntStateOf(progress.counts.sum()) }
    LaunchedEffect(progress.counts) {
        val completed = progress.counts.sum()
        if (completed > lastCompleted) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        lastCompleted = completed
    }
    val reviewSettings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, reviewSettings.language) {
        if (reviewSettings.language in com.example.myapplication4.util.RecallLocale.languages) {
            context.createConfigurationContext(Configuration(configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(reviewSettings.language))
            }).resources
        } else context.resources
    }
    if (cards.isEmpty()) {
        val dueFlow: kotlinx.coroutines.flow.Flow<Int?> = remember(vm) { vm.remainingDueReviews() }
        val due by dueFlow.collectAsStateWithLifecycle(initialValue = null)
        if (due == null) { ScreenFrame { RecallTopBar(s(R.string.ui_review), onBack = done); CircularProgressIndicator() }; return }
        ScreenFrame { RecallTopBar(s(R.string.ui_review), onBack = done); RecallEmptyState(Icons.Outlined.CheckCircle,
            s(if (due!! > 0) R.string.phase3_session_empty else R.string.ui_nothing_due),
            s(if (due!! > 0) R.string.phase3_session_empty_hint else R.string.ui_reviews_complete), s(R.string.ui_done), done) }
        return
    }
    if (index >= cards.size) {
        val dueFlow: kotlinx.coroutines.flow.Flow<Int?> = remember(vm) { vm.remainingDueReviews() }
        val remaining by dueFlow.collectAsStateWithLifecycle(initialValue = null)
        ReviewComplete(counts.values.sum(), skipped, counts, resources, done, remaining)
        return
    }
    val card = cards[index]
    var previews by remember(card.id, reviewSettings.desiredRetention) { mutableStateOf(vm.preview(card, System.currentTimeMillis(), reviewSettings.desiredRetention)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(card.id, lifecycleOwner, reviewSettings.desiredRetention) {
        val timer = vm.reviewTimer
        timer.begin(card.id, android.os.SystemClock.elapsedRealtime())
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) timer.pause(android.os.SystemClock.elapsedRealtime())
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                timer.resume(android.os.SystemClock.elapsedRealtime())
                previews = vm.preview(card, System.currentTimeMillis(), reviewSettings.desiredRetention)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) timer.pause(android.os.SystemClock.elapsedRealtime())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { timer.pause(android.os.SystemClock.elapsedRealtime()); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(card.id, reviewSettings.desiredRetention) {
        while (true) { kotlinx.coroutines.delay(60_000); if (!vm.reviewProgress.value.saving) previews = vm.preview(card, System.currentTimeMillis(), reviewSettings.desiredRetention) }
    }
    // Only the current question needs these immutable reading annotations. Load
    // once per card instead of maintaining a Room/lifecycle observer per question.
    val targets = produceState(emptyList<PronunciationTarget>(), card.id) {
        value = vm.loadPronunciations(card.id)
    }.value
    DisposableEffect(card.id) { onDispose { vm.pronunciation.stop() } }
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val pagePadding = if (maxWidth < 360.dp) RecallSpacing.md else RecallSpacing.ml
        Column(Modifier.fillMaxSize().padding(horizontal = pagePadding).widthIn(max = RecallSizes.contentMaxWidth).align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().heightIn(min = RecallSizes.touch), verticalAlignment = Alignment.CenterVertically) { RecallIconButton(Icons.Outlined.Close, s(R.string.ui_end_review), done); Column(Modifier.weight(1f).padding(horizontal = RecallSpacing.xs)) { BidiAwareText(card.lessonTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1); BidiAwareText(s(R.string.ui_review_progress, s.number(index + 1), s.number(cards.size)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted) }; CircularProgressIndicator(progress = { (index + 1f) / cards.size }, modifier = Modifier.size(30.dp), strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant) }
            ReviewCardStack(card, targets, cards.size - index, revealed, Modifier.weight(1f).fillMaxWidth())
            TextButton(onClick = {
                // Skipping only advances this session: no review event or memory-state update.
                vm.skipReviewCard(card.id)
            }, enabled = !saving, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = RecallSizes.touch)) {
                Text(resources.getString(R.string.review_skip))
            }
            if (!revealed) RecallPrimaryButton(s(R.string.ui_show_answer), Icons.Outlined.Visibility, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.revealReviewAnswer() }, Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = RecallSpacing.md))
            else RatingBar(previews, enabled = !saving) { result ->
                if (!saving) {
                    vm.submitReview(card, previews, result.rating) { previews = it }
                }
            }
        }
    }
}

@Composable private fun RatingBar(previews: Map<Rating, ScheduleResult>, enabled: Boolean, rate: (ScheduleResult) -> Unit) {
    val s = recallStrings()
 Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = RecallSpacing.md)) { BidiAwareText(s(R.string.ui_rate_question), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(bottom = RecallSpacing.xs)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { Rating.entries.forEach { rating -> val result = previews.getValue(rating); RatingChoice(rating, s.interval(result.intervalMillis), Modifier.weight(1f), enabled) { rate(result) } } } } }

@Composable private fun RatingChoice(rating: Rating, interval: String, modifier: Modifier, enabled: Boolean, click: () -> Unit) {
    val s = recallStrings()
 val tint = when(rating) { Rating.AGAIN -> MaterialTheme.colorScheme.error; Rating.HARD -> MaterialTheme.colorScheme.warning; Rating.GOOD -> MaterialTheme.colorScheme.primary; Rating.EASY -> MaterialTheme.colorScheme.success }; Surface(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 68.dp), color = MaterialTheme.colorScheme.surfaceInteractive, contentColor = MaterialTheme.colorScheme.onSurface, shape = RecallRadii.medium) { Column(Modifier.padding(vertical = RecallSpacing.sm, horizontal = RecallSpacing.xxs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(s.rating(rating), style = MaterialTheme.typography.labelLarge, color = tint); BidiAwareText(interval, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted) } } }

@Composable
private fun ReviewComplete(total: Int, skipped: Int, counts: Map<Rating, Int>, resources: android.content.res.Resources, done: () -> Unit, remaining: Int?) {
    val s = recallStrings()

    ScreenFrame {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceSelected), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(s(R.string.ui_review_complete), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = RecallSpacing.lg))
            Text(resources.getQuantityString(R.plurals.review_answered_count, total, total), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.muted)
            if (skipped > 0) Text(resources.getQuantityString(R.plurals.review_skipped_count, skipped, skipped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
            remaining?.let { Text(resources.getString(R.string.phase3_remaining, s.number(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted) }
            Spacer(Modifier.height(RecallSpacing.xl))
            Rating.entries.forEach { rating ->
                Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.xs)) {
                    Text(s.rating(rating), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(s.number(counts[rating] ?: 0), style = MaterialTheme.typography.titleMedium)
                }
            }
            RecallPrimaryButton(s(R.string.ui_done), onClick = done, modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.xl))
        }
    }
}

@Composable
fun ImportScreen(vm: RecallViewModel, targetSubjectId: String? = null, targetLessonId: String? = null, back: () -> Unit) {
    val s = recallStrings()

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
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedLesson = lessons.firstOrNull { it.id == targetLessonId }
    if(destinationPicker) SettingsOptionsSheet(s(R.string.ui_import_destination), listOf<String?>(null).map { it to s(R.string.ui_subject_from_json) } + subjects.map { it.id to it.name }, selectedSubjectId, { selectedSubjectId = it; destinationPicker = false }) { destinationPicker = false }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    com.example.myapplication4.util.readBoundedText(it, 2_000_000)
                } ?: error("Unable to read file")
            } }.fold(
            onSuccess = { text ->
                if (text.length > 2_000_000) notice = s(R.string.ui_file_too_large)
                else { raw = text; result = null; notice = s(R.string.ui_json_loaded, s.number(text.length)) }
            },
            onFailure = { notice = s(if (it is IllegalArgumentException) R.string.ui_file_too_large else R.string.ui_json_read_error) },
        )
            } finally { busy = false }
        }
    }
    fun pasteFromClipboard() {
        val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (pasted.isBlank()) notice = s(R.string.ui_clipboard_empty)
        else { raw = pasted; result = null; notice = s(R.string.ui_pasted_count, s.number(pasted.length)) }
    }
    ScreenFrame {
        Column(Modifier.fillMaxSize()) {
            RecallTopBar(s(R.string.ui_import_ai), s(R.string.ui_import_hint), back)
            TextButton({ destinationPicker = true }, enabled = targetSubjectId == null) { Text(s(R.string.ui_destination, selectedSubject?.name ?: s(R.string.ui_subject_from_json))) }
            if(selectedLesson != null) Text(s(R.string.ui_append_to, selectedLesson.title), style = MaterialTheme.typography.labelLarge)
            if (result !is ImportResult.Success) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(::pasteFromClipboard, enabled = !busy) { Icon(Icons.Outlined.ContentPaste, null); Spacer(Modifier.width(RecallSpacing.xxs)); Text(s(R.string.ui_paste)) }
                    TextButton({ openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(RecallSpacing.xxs)); Text(s(R.string.ui_open_file)) }
                    Spacer(Modifier.weight(1f))
                    if (raw.isNotEmpty()) Text(s(R.string.ui_char_count, s.number(raw.length)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted)
                }
                OutlinedTextField(
                    raw,
                    { raw = it; result = null; notice = null },
                    Modifier.fillMaxWidth().weight(1f),
                    label = { Text(s(R.string.ui_recall_json)) },
                    placeholder = { Text(s(R.string.ui_paste_complete)) },
                    shape = RecallRadii.medium,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                    enabled = !busy,
                )
                notice?.let { Text(it, color = MaterialTheme.colorScheme.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = RecallSpacing.xs)) }
                if (result is ImportResult.Failure) Text(s.importError((result as ImportResult.Failure).message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = RecallSpacing.xs))
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = RecallSpacing.md), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
                    OutlinedButton({ clipboard.setPrimaryClip(android.content.ClipData.newPlainText(s(R.string.ui_ai_prompt_clip), selectedSubject?.let { subjectAiPrompt(it.name, selectedLesson?.title) } ?: AI_PROMPT)) }, Modifier.weight(1f).heightIn(min = RecallSizes.buttonHeight), shape = RecallRadii.medium) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(RecallSpacing.xs)); Text(s(R.string.ui_ai_prompt)) }
                    RecallPrimaryButton(if (busy) s(R.string.ui_processing) else s(R.string.ui_preview), Icons.AutoMirrored.Outlined.ArrowForward, {
                        if (!busy) scope.launch {
                            busy = true
                            val source = raw
                            val language = selectedLesson?.learningLanguage
                            try { result = withContext(Dispatchers.Default) { RecallImportParser.parse(source, language) } }
                            finally { busy = false }
                        }
                    }, Modifier.weight(1f), raw.isNotBlank() && !busy)
                }
            } else ImportPreview((result as ImportResult.Success).draft, { result = null }) { draft -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.importDraft(draft, back, selectedSubjectId, targetLessonId) }
        }
    }
}

@Composable internal fun ColumnScope.ImportPreview(initial: ImportDraft, editSource: () -> Unit, import: (ImportDraft) -> Unit) {
    val s = recallStrings()

    var cards by remember(initial) { mutableStateOf(initial.cards) }
    var editing by remember { mutableStateOf<Int?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.count(R.plurals.ui_cards_ready, cards.count { it.included && !it.duplicate }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(editSource) { Text(s(R.string.ui_edit_json)) }
    }
    Text(s(R.string.ui_preview_check), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    if (initial.pronunciationWarnings > 0) Text(s(R.string.ui_targets_skipped, s.number(initial.pronunciationWarnings)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.warning)
    if (cards.any { it.pronunciationTargets.isNotEmpty() }) Text(androidx.compose.ui.res.stringResource(com.example.myapplication4.R.string.pronunciation_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = RecallSpacing.md)) {
        itemsIndexed(cards, key = { index, _ -> index }) { index, card ->
            Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalAlignment = Alignment.Top) {
                Checkbox(card.included && !card.duplicate, { checked -> cards = cards.toMutableList().also { it[index] = card.copy(included = checked) } }, enabled = !card.duplicate)
                Column(Modifier.weight(1f)) {
                    PronounceableStudyText(card.front, "front", card.pronunciationTargets, initial.learningLanguage, style = MaterialTheme.typography.titleSmall)
                    PronounceableStudyText(card.back, "back", card.pronunciationTargets, initial.learningLanguage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, maxLines = 3)
                    if(card.duplicate) Text(s(R.string.ui_duplicate_excluded), color = MaterialTheme.colorScheme.warning)
                }
                RecallIconButton(Icons.Outlined.Edit, s(R.string.ui_edit_import_card)) { editing = index }
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
    RecallPrimaryButton(s(R.string.ui_import_cards), Icons.Outlined.FileDownload, { import(initial.copy(cards = cards)) }, Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = RecallSpacing.md), cards.any { it.included && !it.duplicate })
}

private enum class SettingsPanel { Retention, NewCards, Pause, Appearance, Language, Scheduler, SpeechRate }

@Composable
fun SettingsScreen(vm: RecallViewModel, openInfo: () -> Unit) {
    val s = recallStrings()

    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editSchedule by remember { mutableStateOf<ScheduleBlockEntity?>(null) }
    var add by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<SettingsPanel?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingBackup by remember { mutableStateOf<android.net.Uri?>(null) }
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
                vm.setDebugMode(true); schedulerTaps = 0; panel = null; message = s(R.string.ui_debug_enabled)
                return
            }
        }
        panel = SettingsPanel.Scheduler
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setRemindersEnabled(granted)
        if (!granted) message = s(R.string.ui_permission_denied)
    }
    fun enableReminders(enabled: Boolean) {
        if (!enabled) vm.setRemindersEnabled(false)
        else if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) vm.setRemindersEnabled(true)
        else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackupFile(uri) { message = it }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingBackup = uri
    }
    ScreenFrame {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = RecallSpacing.lg)) {
            item {
                RecallTopBar(s(R.string.ui_settings), s(R.string.ui_settings_hint))
                val infoResources = recallInfoResources(settings.language)
                SettingRow(Icons.Outlined.Info, infoResources.getString(R.string.info_title), infoResources.getString(R.string.info_subtitle), openInfo)
                message?.let { Surface(color = MaterialTheme.colorScheme.surfaceSelected, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RecallRadii.medium, modifier = Modifier.fillMaxWidth().padding(bottom = RecallSpacing.sm)) { Text(it, Modifier.padding(RecallSpacing.sm), style = MaterialTheme.typography.bodySmall) } }
                SettingsSection(s(R.string.ui_study))
                SettingRow(Icons.Outlined.TrackChanges, s(R.string.ui_retention), java.text.NumberFormat.getPercentInstance(s.locale).format(settings.desiredRetention) + " · " + if(settings.desiredRetention >= .93) s(R.string.ui_retention_longer) else s(R.string.ui_retention_balanced)) { panel = SettingsPanel.Retention }
                SettingRow(Icons.Outlined.Style, s(R.string.ui_new_cards_per_review), if(settings.newCardLimit == 0) s(R.string.ui_reviews_only) else s(R.string.ui_new_cards_limit, s.number(settings.newCardLimit))) { panel = SettingsPanel.NewCards }
                SettingsSection(s(R.string.ui_study_schedule), s(R.string.ui_add)) { add = true }
                Text(s(R.string.ui_quiet_override), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(bottom = RecallSpacing.sm))
            }
            itemsIndexed(schedules, key = { _, it -> it.id }) { _, schedule -> ScheduleRow(schedule, { editSchedule = schedule }) { vm.deleteSchedule(schedule.id) } }
            item {
                SettingsSection(s(R.string.ui_notifications))
                ToggleSettingRow(Icons.Outlined.NotificationsNone, s(R.string.ui_review_reminders), s(R.string.ui_outside_quiet), settings.remindersEnabled, ::enableReminders)
                ToggleSettingRow(Icons.Outlined.Schedule, s(R.string.ui_window_reminder), s(R.string.ui_window_reminder_hint), settings.studyWindowReminder, vm::setStudyWindowReminder)
                SettingRow(Icons.Outlined.Snooze, s(R.string.ui_pause_reminders), settings.pausedUntil?.let { s(R.string.ui_paused_until, s.date(it, time = true)) } ?: s(R.string.ui_not_paused)) { panel = SettingsPanel.Pause }
                BackgroundReminderSettings { enableReminders(true) }
                SettingsSection(s(R.string.ui_appearance_language))
                SettingRow(Icons.Outlined.Contrast, s(R.string.ui_appearance), s(when(settings.themeMode) { "light" -> R.string.ui_light; "dark" -> R.string.ui_dark; else -> R.string.ui_system })) { panel = SettingsPanel.Appearance }
                ToggleSettingRow(Icons.Outlined.Palette, s(R.string.ui_dynamic_color), s(R.string.ui_dynamic_hint), settings.dynamicColor, vm::setDynamicColor, enabled = Build.VERSION.SDK_INT >= 31)
                SettingRow(Icons.Outlined.Language, s(R.string.ui_language), languageChoices(s).firstOrNull { it.first == settings.language }?.second ?: s(R.string.ui_system)) { panel = SettingsPanel.Language }
                SettingsSection(androidx.compose.ui.res.stringResource(com.example.myapplication4.R.string.pronunciation))
                SettingRow(Icons.Outlined.RecordVoiceOver, androidx.compose.ui.res.stringResource(com.example.myapplication4.R.string.speech_rate), when(settings.speechRate) { .8f -> s(R.string.ui_slow); 1.2f -> s(R.string.ui_fast); else -> s(R.string.ui_normal) }) { panel = SettingsPanel.SpeechRate }
                SettingRow(Icons.Outlined.Language, s(R.string.ui_android_voices), s(R.string.ui_offline_voices_hint)) { vm.pronunciation.manageVoices(context) }
                SettingsSection(s(R.string.ui_data))
                archiveStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = RecallSpacing.sm)) }
                if (archiveBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                SettingRow(Icons.Outlined.Archive, s(R.string.ui_export_all), s(R.string.ui_export_all_hint)) { if (!archiveBusy) fullExport.launch("Recall-all-data-" + System.currentTimeMillis() + ".zip") }
                SettingRow(Icons.Outlined.Restore, s(R.string.ui_restore_full), s(R.string.ui_restore_full_hint)) { if (!archiveBusy) fullRestore.launch(arrayOf("application/zip", "application/octet-stream")) }
                SettingRow(Icons.Outlined.FileUpload, s(R.string.ui_export_data_only), s(R.string.ui_export_data_hint)) { export.launch("Recall-backup-" + System.currentTimeMillis() + ".json") }
                SettingRow(Icons.Outlined.FileDownload, s(R.string.ui_import_backup), s(R.string.ui_import_backup_hint)) { openBackup.launch(arrayOf("application/json", "text/plain")) }
                SettingsSection(s(R.string.ui_advanced))
                SettingRow(Icons.Outlined.Memory, s(R.string.ui_scheduler_info), s(R.string.ui_scheduler_hint)) { schedulerTap() }
                if (settings.debugMode) ReminderDebugPanel(vm) { message = it }
            }
        }
    }
    editSchedule?.let { value -> ScheduleSheet(initial = value, save = { name, type, start, end, days -> vm.editSchedule(value.copy(name = name, type = type, startMinute = start, endMinute = end, days = days.sorted().joinToString(","))); editSchedule = null }) { editSchedule = null } }
    if (add) ScheduleSheet(save = { n,t,s,e,d -> vm.saveSchedule(n,t,s,e,d); add=false }) { add=false }
    when (panel) {
        SettingsPanel.SpeechRate -> SettingsOptionsSheet(s(R.string.speech_rate), listOf(.8f to s(R.string.ui_slow), 1f to s(R.string.ui_normal), 1.2f to s(R.string.ui_fast)), settings.speechRate, { vm.setSpeechRate(it); panel = null }) { panel = null }
        SettingsPanel.Retention -> SettingsOptionsSheet(s(R.string.ui_retention), listOf(.85 to R.string.ui_fewer_reviews, .90 to R.string.ui_retention_balanced, .93 to R.string.ui_stronger_retention, .95 to R.string.ui_most_reviews).map { (retention, label) -> retention to (java.text.NumberFormat.getPercentInstance(s.locale).format(retention) + " · " + s(label)) }, settings.desiredRetention, { vm.setRetention(it); panel = null }) { panel = null }
        SettingsPanel.NewCards -> SettingsOptionsSheet(s(R.string.ui_new_cards_per_review), listOf(0, 10, 20, 30, 50).map { it to if(it == 0) s(R.string.ui_reviews_only) else s.count(R.plurals.ui_cards, it) }, settings.newCardLimit, { vm.setNewCardLimit(it); panel = null }) { panel = null }
        SettingsPanel.Appearance -> SettingsOptionsSheet(s(R.string.ui_appearance), listOf("system" to s(R.string.ui_system), "light" to s(R.string.ui_light), "dark" to s(R.string.ui_dark)), settings.themeMode, { vm.setThemeMode(it); panel = null }) { panel = null }
        SettingsPanel.Language -> SettingsOptionsSheet(s(R.string.ui_language), languageChoices(s), settings.language, { vm.setLanguage(it); panel = null }) { panel = null }
        SettingsPanel.Pause -> PauseRemindersSheet({ vm.pauseReminders(it); panel = null }) { panel = null }
        SettingsPanel.Scheduler -> AlertDialog(onDismissRequest = { panel = null }, icon = { Icon(Icons.Outlined.Memory, null) }, title = { Column { Text(s(R.string.ui_scheduler_info), modifier = Modifier.clickable { schedulerTap() }); if (!settings.debugMode) Text(s(R.string.ui_debug_taps, s.number((6 - schedulerTaps).coerceAtLeast(0))), style = MaterialTheme.typography.bodySmall) } }, text = { Text(s(R.string.ui_scheduler_body)) }, confirmButton = { TextButton({ panel = null }) { Text(s(R.string.ui_done)) } })
        null -> Unit
    }
    pendingArchive?.let { uri ->
        AlertDialog(onDismissRequest = { pendingArchive = null }, title = { Text(s(R.string.ui_restore_all_question)) },
            text = { Text(s(R.string.ui_restore_all_body)) },
            confirmButton = { TextButton({ pendingArchive = null; vm.restoreAllData(uri) }) { Text(s(R.string.ui_restore)) } },
            dismissButton = { TextButton({ pendingArchive = null }) { Text(s(R.string.ui_cancel)) } })
    }
    pendingBackup?.let { uri -> AlertDialog(onDismissRequest = { pendingBackup = null }, icon = { Icon(Icons.Outlined.Restore, null) }, title = { Text(s(R.string.ui_restore_backup_question)) }, text = { Text(s(R.string.ui_restore_backup_body)) }, confirmButton = { TextButton({ pendingBackup = null; vm.importBackupFile(uri) { message = it } }) { Text(s(R.string.ui_restore)) } }, dismissButton = { TextButton({ pendingBackup = null }) { Text(s(R.string.ui_cancel)) } }) }
}

@Composable private fun SettingsSection(title: String, action: String? = null, click: () -> Unit = {}) = SectionHeader(title, action, click)
@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(onClick = click).padding(vertical = RecallSpacing.sm, horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(RecallSizes.icon), tint = MaterialTheme.colorScheme.muted); Spacer(Modifier.width(RecallSpacing.md)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }; Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.outline) } }
@Composable private fun ToggleSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) { Row(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(enabled = enabled) { change(!checked) }.padding(vertical = RecallSpacing.xs, horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(RecallSizes.icon), tint = if(enabled) MaterialTheme.colorScheme.muted else MaterialTheme.colorScheme.outline); Spacer(Modifier.width(RecallSpacing.md)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, color = if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.muted); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }; Switch(checked, change, enabled = enabled) } }
@Composable private fun ScheduleRow(block: ScheduleBlockEntity, edit: () -> Unit, delete: () -> Unit) {
    val s = recallStrings()
 Row(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.sm), verticalAlignment = Alignment.CenterVertically) { val quiet = block.type == "quiet"; Box(Modifier.size(42.dp).clip(RecallRadii.medium).background(if(quiet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer), contentAlignment=Alignment.Center){Icon(if(quiet)Icons.Outlined.DoNotDisturbOn else Icons.Outlined.Schedule,null,Modifier.size(20.dp), tint = if(quiet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)};Spacer(Modifier.width(RecallSpacing.sm));Column(Modifier.weight(1f)){BidiAwareText(block.name,style=MaterialTheme.typography.titleSmall);Text(daysLabel(block.days, s) + " · " + clock(block.startMinute, s) + "–" + clock(block.endMinute, s),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.muted)};RecallIconButton(Icons.Outlined.Edit,s(R.string.ui_edit_schedule),edit);RecallIconButton(Icons.Outlined.Delete,s(R.string.ui_delete_schedule, block.name),delete)} }
private fun daysLabel(days: String, s: com.example.myapplication4.util.RecallStrings) = if(days == "1,2,3,4,5,6,7") s(R.string.ui_daily) else days.split(',').mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 } }.joinToString(" · ", transform = s::weekday)
private fun clock(minutes: Int, s: com.example.myapplication4.util.RecallStrings): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, s.locale).format(java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, minutes / 60); set(java.util.Calendar.MINUTE, minutes % 60) }.time)
private fun languageChoices(s: com.example.myapplication4.util.RecallStrings) = listOf("system" to s(R.string.ui_system), "en" to "English", "ar" to "العربية", "es" to "Español", "fr" to "Français", "de" to "Deutsch")
