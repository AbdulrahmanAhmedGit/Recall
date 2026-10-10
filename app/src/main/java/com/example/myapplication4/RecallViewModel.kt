package com.example.myapplication4

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.example.myapplication4.notifications.*
import java.util.Calendar
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.YearMonth

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecallViewModel @JvmOverloads constructor(app: Application, private val database: RecallDatabase = RecallDatabase.create(app)) : AndroidViewModel(app) {
    private fun ui(id: Int, vararg args: Any): String = com.example.myapplication4.util.RecallLocale.context(getApplication(), settings.value.language).getString(id, *args)
    val pronunciation = com.example.myapplication4.pronunciation.PronunciationManager(app) { settings.value.language }
    override fun onCleared() { pronunciation.close(); database.close(); super.onCleared() }
    fun pronunciations(cardId: String) = dao.pronunciationTargets(cardId).map { rows -> rows.map { it.target() } }
    suspend fun loadPronunciations(cardId: String) = dao.targetsForCard(cardId).map { it.target() }
    private val dao = database.dao()
    private val preferences = UserPreferences(app)
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    var reviewIsPractice = false
        private set
    val introductionSeen: StateFlow<Boolean?> = preferences.introductionSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun completeIntroduction() = viewModelScope.launch {
        try { preferences.markIntroductionSeen() }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            notice.value = ui(R.string.ui_preference_save_error)
        }
    }
    private val scheduler: ReviewScheduler = FsrsScheduler()
    // Kept across Activity recreation without putting full card text in Android's
    // size-limited saved-state Bundle. Process recreation returns safely to Today.
    internal var activeReviewCards: List<CardWithLesson>? = null
        set(value) {
            if (value != null && field !== value) reviewProgress.value = ReviewSessionProgress()
            field = value
        }
    val reviewProgress = MutableStateFlow(ReviewSessionProgress())
    val reviewTimer = ReviewTimer()
    private var deferredBatchCards = emptyList<String>()
    fun revealReviewAnswer() { reviewProgress.value = reviewProgress.value.copy(revealed = true) }
    fun skipReviewCard(presentedId: String) {
        val old = reviewProgress.value
        if (activeReviewCards?.getOrNull(old.index)?.id != presentedId) return
        if (!old.saving) {
            if (!reviewIsPractice) activeReviewCards?.getOrNull(old.index)?.let { card -> deferredBatchCards = (deferredBatchCards + card.id).distinct().takeLast(BacklogPolicy.batchSize) }
            reviewProgress.value = old.copy(index = old.index + 1, revealed = false, skipped = old.skipped + 1)
        }
    }
    val subjects = dao.subjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val schedules = dao.schedules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val activityRefresh = MutableStateFlow(0L)
    // One shared ticker, stopped when no screen subscribes. Due counts used to freeze at
    // ViewModel creation, so cards becoming due while the app was open stayed invisible.
    private val clock = activityRefresh.flatMapLatest {
        flow {
            while (true) { emit(System.currentTimeMillis()); delay(60_000) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    val lessons = clock.flatMapLatest { dao.lessonOverviews(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val pauseContext = combine(settings, clock, dao.pauseLessonScopes()) { prefs, now, scopes -> now to prefs.reviewPauses.excludedLessonIds(now, scopes) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis() to emptyList<String>())
    val reviewPauses = combine(settings, clock) { prefs, now -> prefs.reviewPauses.filter { it.endAt > now } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allChapters = dao.observeChapters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pausedLessonIds = pauseContext.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueCount = pauseContext
        .flatMapLatest { (now, excluded) -> dao.dueCount(now, excluded) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun remainingDueReviews(): Flow<Int> = pauseContext
        .flatMapLatest { (now, excluded) -> dao.dueCount(now, excluded) }
    private val reviewInvalidations = database.invalidationTracker.createFlow("ReviewLogEntity")
    val todayReviews = combine(clock, reviewInvalidations) { _, _ ->
        dao.reviewCountSnapshot(todayStart(), System.currentTimeMillis())
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val localDays = clock.map { ActivityPeriod.current() }.distinctUntilChanged()
    val studyActivity = combine(clock, reviewInvalidations) { _, _ ->
        val now = System.currentTimeMillis()
        val period = ActivityPeriod.current()
        val rows = dao.studyActivitySnapshot(studyActivityQuery(period, now))
        StudyActivity(period, rows.associate { LocalDate.ofEpochDay(it.epochDay) to it.reviewCount })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyActivity(ActivityPeriod.current()))
    private val recallHistory = localDays.flatMapLatest { day ->
        val period = InsightsPeriod(day.today, day.zone)
        var previousEvents: List<RecallReviewEvent>? = null
        var previousAnalysis: RecallHistoryAnalysis? = null
        var nextFutureAt = Long.MAX_VALUE
        var analyzedAt = Long.MIN_VALUE
        combine(dao.insightsReviews(period.historyStart, period.end), clock) { events, _ ->
            // A newly committed response can be newer than the last minute tick.
            val now = System.currentTimeMillis()
            // Minute ticks refresh urgency, but unchanged history need not be sorted again.
            if (previousEvents === events && previousAnalysis != null && now >= analyzedAt && now < nextFutureAt) previousAnalysis!!
            else analyzeRecallHistory(events, period, now).also {
                previousEvents = events
                previousAnalysis = it
                analyzedAt = now
                nextFutureAt = events.asSequence().map { event -> event.reviewedAt }.filter { at -> at > now }.minOrNull() ?: Long.MAX_VALUE
            }
        }.distinctUntilChanged()
    }.flowOn(Dispatchers.Default)
    private val predictedRecall = run {
        val predictor = CurrentRecallPredictor()
        combine(dao.insightsPredictionCards(), clock) { cards, _ ->
            // Room can publish a completed response after the last minute tick.
            predictor.calculate(cards, System.currentTimeMillis())
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }
    val learningInsights: StateFlow<LearningInsights?> = recallHistory.flatMapLatest { history ->
        val ids = history.attention.map { it.example.cardId }
        val details = if (ids.isEmpty()) flowOf(emptyList()) else dao.insightsAttentionCards(ids)
        combine(dao.insightsMemoryCounts(InsightsPolicy.matureStabilityDays, Double.MAX_VALUE), details, clock, predictedRecall) { memory, cards, now, prediction ->
            val byId = cards.associateBy { it.cardId }
            LearningInsights(memory, history, history.attention.mapNotNull { group ->
                byId[group.example.cardId]?.let { AttentionLesson(group, it) }
            }, now, prediction)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun calendarDays(month: YearMonth): Flow<List<ReviewCalendarDay>> = localDays.flatMapLatest { day ->
        dao.reviewCalendarCounts(reviewCalendarQuery(ReviewCalendarPeriod(month, day.today, day.zone)))
    }.flowOn(Dispatchers.Default)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun calendarCards(date: LocalDate, limit: Int): Flow<List<CardWithLesson>> = localDays.flatMapLatest { day ->
        val (start, end) = calendarDayBounds(date, day.today, day.zone)
        dao.reviewCalendarCards(start, end, limit.coerceAtLeast(100))
    }
    private fun todayStart(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    fun addSubject(name: String, accent: String = "blue") = change { if (name.isNotBlank()) dao.insertSubject(SubjectEntity(name = name.trim(), accent = accent)) }
    fun chapters(subjectId: String): Flow<List<ChapterEntity>> = dao.chapters(subjectId)
    fun cards(lessonId: String): Flow<List<CardEntity>> = dao.cardsForLesson(lessonId)
    fun tags(lessonId: String): Flow<List<TagEntity>> = dao.tagsForLesson(lessonId)
    fun addChapter(subjectId: String, name: String) = change { if (name.isNotBlank()) dao.insertChapter(ChapterEntity(subjectId = subjectId, name = name.trim())) }
    fun addLesson(subjectId: String, chapterId: String?, title: String, summary: String?, tags: List<String> = emptyList()) = change {
        if (title.isBlank()) return@change
        val lesson = LessonEntity(subjectId = subjectId, chapterId = chapterId, title = title.trim(), summary = summary?.trim()?.takeIf { it.isNotEmpty() })
        database.withTransaction { dao.insertLesson(lesson); attachTags(lesson.id, tags) }
    }
    fun addCard(lessonId: String, front: String, back: String, hint: String? = null, type: String = "qa", targets: List<PronunciationTarget> = emptyList()) = change {
        if (front.isBlank() || back.isBlank()) return@change
        val card = CardEntity(lessonId = lessonId, front = front.trim(), back = back.trim(), hint = hint?.trim()?.takeIf { it.isNotEmpty() }, type = type)
        database.withTransaction { dao.insertCard(card); dao.saveState(ReviewStateEntity(cardId = card.id)); saveTargets(card, targets) }
    }
    private suspend fun attachTags(lessonId: String, tags: List<String>) {
        tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.forEach { name ->
            val tag = dao.findTag(name) ?: TagEntity(name = name).also { dao.insertTag(it) }
            dao.tagLesson(LessonTagEntity(lessonId, tag.id))
        }
    }
    private fun applyNewCardLimit(cards: List<CardWithLesson>): List<CardWithLesson> {
        val limit = settings.value.newCardLimit
        var newCards = 0
        return cards.filter { it.reps > 0 || newCards++ < limit }
    }
    val reviewLoading = MutableStateFlow(false)
    private fun loadReview(load: suspend () -> List<CardWithLesson>, onLoaded: (List<CardWithLesson>) -> Unit, practice: Boolean = false) = viewModelScope.launch {
        if (reviewLoading.value) return@launch
        reviewLoading.value = true
        try {
            val cards = withContext(Dispatchers.Default) { load().let { if (practice) it else applyNewCardLimit(it) } }
            reviewIsPractice = practice
            onLoaded(cards)
        }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { notice.value = ui(R.string.ui_review_load_error) }
        finally { reviewLoading.value = false }
    }
    fun reviewDue(onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({
        val now = System.currentTimeMillis()
        dao.dueCards(now, excludedLessonIds = preferences.settings.first().reviewPauses.excludedLessonIds(now, dao.pauseLessonScopes().first()))
    }, onLoaded)
    fun reviewBatch(onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({
        val now = System.currentTimeMillis()
        val excluded = preferences.settings.first().reviewPauses.excludedLessonIds(now, dao.pauseLessonScopes().first())
        dao.dueBatch(now, settings.value.newCardLimit, BacklogPolicy.batchSize, deferredBatchCards, excluded).ifEmpty {
            dao.dueBatch(now, settings.value.newCardLimit, BacklogPolicy.batchSize, excludedLessonIds = excluded)
        }
    }, onLoaded)
    fun reviewSubject(subjectId: String, onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({ dao.dueCards(System.currentTimeMillis(), subjectId) }, onLoaded)
    fun reviewLesson(lessonId: String, onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({ dao.lessonCards(lessonId) }, onLoaded)
    fun reviewFocus(focus: ReviewFocus, practice: Boolean, onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({
        dao.focusCards(focus.subjectId, focus.chapterId, focus.lessonId, System.currentTimeMillis(), practice, newLimit = settings.value.newCardLimit)
    }, onLoaded, practice)
    fun setLessonPause(lessonId: String, pause: LessonReviewPause?) = change {
        preferences.setLessonPause(lessonId, pause)
        activityRefresh.value += 1
        refreshReminders()
    }
    fun setReviewPause(scope: ReviewPauseScope, targetId: String, pause: ReviewPause?) = change {
        preferences.setReviewPause(scope, targetId, pause)
        activityRefresh.value += 1
        refreshReminders()
    }
    fun preview(card: CardWithLesson, reviewedAt: Long, retention: Double = settings.value.desiredRetention): Map<Rating, ScheduleResult> =
        scheduler.preview(card.reviewState(), reviewedAt, retention)
    fun rate(card: CardWithLesson, result: ScheduleResult, elapsedMillis: Long, onSaved: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
        require(result.state.cardId == card.id)
        val log = ReviewLogEntity(
            cardId = card.id,
            reviewedAt = result.reviewedAt,
            rating = result.rating.value,
            previousInterval = result.previousDays,
            nextInterval = result.nextDays,
            previousStability = card.stability,
            newStability = result.state.stability,
            durationMillis = elapsedMillis.coerceAtLeast(0),
            previousDueAt = result.previousDueAt,
            nextDueAt = result.state.dueAt,
            elapsedDays = result.elapsedDays,
            previousDifficulty = card.difficulty,
            newDifficulty = result.state.difficulty,
            previousState = result.previousState,
            newState = result.state.state,
            reps = result.state.reps,
            lapses = result.state.lapses,
        )
        val saved = dao.commitReviewIfCurrent(card.reviewState(), result.state, log, card)
        if (!saved) notice.value = ui(R.string.ui_card_changed_error)
        onSaved(saved)
        if (saved) queueReminderRefresh()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            notice.value = ui(R.string.ui_answer_save_error)
            onSaved(false)
        }
    }
    fun submitReview(card: CardWithLesson, shown: Map<Rating, ScheduleResult>, rating: Rating, refresh: (Map<Rating, ScheduleResult>) -> Unit) {
        if (reviewProgress.value.saving) return
        if (activeReviewCards?.getOrNull(reviewProgress.value.index)?.id != card.id) return
        if (reviewIsPractice) {
            val old = reviewProgress.value
            reviewProgress.value = old.copy(index = old.index + 1, revealed = false,
                counts = old.counts.mapIndexed { i, count -> count + if (i == rating.ordinal) 1 else 0 })
            return
        }
        val now = System.currentTimeMillis()
        val fresh = preview(card, now)
        if (card.lastReviewedAt?.let { now < it } == true || !sameReviewIntervals(shown, fresh)) {
            refresh(fresh)
            notice.value = ui(R.string.phase3_review_refreshed)
            return
        }
        val sessionCards = activeReviewCards
        val old = reviewProgress.value
        reviewProgress.value = old.copy(saving = true)
        rate(card, fresh.getValue(rating), reviewTimer.duration(android.os.SystemClock.elapsedRealtime())) { saved ->
            // A completion callback must never advance a different/new session.
            if (activeReviewCards === sessionCards) {
                reviewProgress.value = if (saved) old.copy(index = old.index + 1, revealed = false,
                    counts = old.counts.mapIndexed { i, count -> count + if (i == rating.ordinal) 1 else 0 })
                else old.copy(saving = false)
            }
        }
    }
    private fun CardWithLesson.reviewState() = ReviewStateEntity(id, state, dueAt, lastReviewedAt, stability, difficulty, scheduledDays, reps, lapses)
    fun setSuspended(id: String, suspended: Boolean) = viewModelScope.launch { dao.setSuspended(id, suspended, System.currentTimeMillis()); refreshReminders() }
    fun deleteCard(id: String) = viewModelScope.launch { dao.deleteCard(id) }
    fun saveSchedule(name: String, type: String, start: Int, end: Int, days: Set<Int>) = viewModelScope.launch { if (name.isNotBlank() && days.isNotEmpty()) dao.saveSchedule(ScheduleBlockEntity(name = name.trim(), type = type, startMinute = start, endMinute = end, days = days.sorted().joinToString(","))); refreshReminders() }
    fun deleteSchedule(id: String) = viewModelScope.launch { dao.deleteSchedule(id); refreshReminders() }
    fun deleteSubject(id: String, onComplete: () -> Unit = {}) = change {
        val keys = dao.lessonIdsForSubject(id).map { ReviewPauseScope.LESSON to it } + dao.chapters(id).first().map { ReviewPauseScope.CHAPTER to it.id } + (ReviewPauseScope.SUBJECT to id)
        preferences.removeReviewPauses(keys.toSet())
        dao.deleteSubject(id); onComplete()
    }
    fun deleteChapter(id: String) = change { preferences.removeReviewPauses(setOf(ReviewPauseScope.CHAPTER to id)); dao.deleteChapter(id) }
    fun deleteLesson(id: String, onComplete: () -> Unit = {}) = change {
        preferences.removeLessonPauses(setOf(id))
        dao.deleteLesson(id); onComplete()
    }
    fun removeLessonTag(lessonId: String, tagId: String) = viewModelScope.launch { dao.removeLessonTag(lessonId, tagId) }
    fun setSpeechRate(value: Float) = viewModelScope.launch { preferences.setSpeechRate(value) }
    fun setRetention(value: Double) = viewModelScope.launch { preferences.setRetention(value) }
    fun setNewCardLimit(value: Int) = viewModelScope.launch { preferences.setNewCardLimit(value) }
    fun setRemindersEnabled(value: Boolean) = viewModelScope.launch { preferences.setRemindersEnabled(value); if (value) ReminderWorker.schedule(getApplication()) else ReminderWorker.cancel(getApplication()) }
    fun setStudyWindowReminder(value: Boolean) = viewModelScope.launch { preferences.setStudyWindowReminder(value); refreshReminders() }
    fun pauseReminders(until: Long?) = viewModelScope.launch { preferences.pauseUntil(until); refreshReminders() }
    fun setThemeMode(value: String) = viewModelScope.launch { preferences.setThemeMode(value) }
    fun setLanguage(value: String) = viewModelScope.launch { preferences.setLanguage(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { preferences.setDynamicColor(value) }
    fun exportBackupFile(uri: android.net.Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val context = getApplication<Application>()
        var staged: java.io.File? = null
        try {
            withContext(Dispatchers.IO) {
                val file = java.io.File.createTempFile("recall-json-", ".json", context.cacheDir).also { staged = it }
                file.bufferedWriter().use { RecallBackupCodec.write(it, dao.backup(), settings.value) }
                require(file.length() <= 50L * 1024 * 1024)
                context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot write backup")
            }
            onResult(ui(R.string.ui_backup_exported))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { onResult(ui(R.string.ui_backup_write_error)) }
        finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { staged?.delete() } }
    }
    fun importBackupFile(uri: android.net.Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        var committed = false
        try {
            val context = getApplication<Application>()
            val parsed = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { RecallBackupCodec.read(it) } ?: error("Cannot read backup")
            }
            if (parsed !is BackupResult.Success) { onResult(ui(R.string.ui_backup_invalid)); return@launch }
            dao.mergeBackup(parsed.data)
            committed = true
            preferences.restore(parsed.settings)
            refreshReminders()
            onResult(ui(R.string.ui_backup_restored))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { onResult(ui(if (committed) R.string.ui_restore_partial else R.string.ui_backup_restore_error)) }
    }
    suspend fun refreshReminders() {
        if (preferences.settings.first().remindersEnabled) ReminderWorker.schedule(getApplication()) else ReminderWorker.cancel(getApplication())
    }
    private var reminderRefresh: kotlinx.coroutines.Job? = null
    private fun queueReminderRefresh() {
        reminderRefresh?.cancel()
        reminderRefresh = viewModelScope.launch {
            delay(2_000) // Coalesce rapid answers instead of waking WorkManager for every tap.
            try { refreshReminders() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Persistent periodic work will retry the reminder check. */ }
        }
    }
    fun onResume() = viewModelScope.launch { activityRefresh.value = System.nanoTime(); refreshReminders() }
    val pendingReviewLink = MutableStateFlow(false)
    fun openReviewLink() { pendingReviewLink.value = true }
    fun setDebugMode(value: Boolean) = viewModelScope.launch { preferences.setDebugMode(value) }
    fun testNotification(result: (String) -> Unit) = viewModelScope.launch {
        if (!preferences.settings.first().debugMode) return@launch
        result(ReminderNotifications.post(com.example.myapplication4.util.RecallLocale.context(getApplication(), settings.value.language), dao.reminderDueCount(System.currentTimeMillis()), test = true) ?: ui(R.string.ui_test_posted))
    }
    val archiveBusy = MutableStateFlow(false)
    val archiveStatus = MutableStateFlow<String?>(null)
    fun exportAllData(uri: android.net.Uri) = viewModelScope.launch {
        if (archiveBusy.value) return@launch
        archiveBusy.value = true; archiveStatus.value = ui(R.string.ui_preparing_archive)
        val context = getApplication<Application>()
        var staged: java.io.File? = null
        try {
            withContext(Dispatchers.IO) {
                val file = java.io.File.createTempFile("recall-export-", ".zip", context.cacheDir)
                staged = file
                FullBackupArchive.write(file.outputStream(), dao.backup(), preferences.settings.first()) { source ->
                    context.contentResolver.openInputStream(android.net.Uri.parse(source)) ?: error("File unavailable")
                }
                archiveStatus.value = ui(R.string.ui_writing_archive)
                context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot write destination")
            }
            archiveStatus.value = ui(R.string.ui_all_exported)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { archiveStatus.value = ui(R.string.ui_export_failed) }
        finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { staged?.delete() }; archiveBusy.value = false }
    }
    fun restoreAllData(uri: android.net.Uri) = viewModelScope.launch {
        if (archiveBusy.value) return@launch
        archiveBusy.value = true; archiveStatus.value = ui(R.string.ui_validating_archive)
        val context = getApplication<Application>()
        var directory: java.io.File? = null
        var committed = false
        try {
            withContext(Dispatchers.IO) {
                val parent = java.io.File(context.filesDir, "materials").apply { mkdirs() }
                val folder = java.io.File(parent, java.util.UUID.randomUUID().toString()).apply { check(mkdir()) }
                directory = folder
                val restored = context.contentResolver.openInputStream(uri)?.use { FullBackupArchive.read(it, folder) } ?: error("Cannot read archive")
                val mapped = restored.data.copy(resources = restored.data.resources.map { resource ->
                    restored.files[resource.id]?.let { file -> resource.copy(uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".materials", file).toString()) } ?: resource
                })
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // Cancellation must not land between Room's commit and recording
                // ownership: cleanup would otherwise delete now-referenced files.
                withContext(kotlinx.coroutines.NonCancellable) {
                    dao.mergeBackup(mapped)
                    committed = true
                }
                // Merging keeps existing records. Do not retain extra attachment copies for ignored IDs.
                val retainedUris = dao.allResources().mapNotNull { it.uri }.toSet()
                mapped.resources.forEach { resource ->
                    if (resource.uri !in retainedUris) restored.files[resource.id]?.delete()
                }
                if (folder.listFiles().isNullOrEmpty()) folder.delete()
                preferences.restore(restored.settings)
            }
            refreshReminders()
            archiveStatus.value = ui(R.string.ui_full_restored)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { archiveStatus.value = if (committed) ui(R.string.ui_restore_partial) else ui(R.string.ui_restore_failed) }
        finally {
            if (!committed) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { directory?.let { folder -> folder.listFiles().orEmpty().forEach { it.delete() }; folder.delete() } }
            archiveBusy.value = false
        }
    }
    val notice = MutableStateFlow<String?>(null)
    private fun change(action: suspend () -> Unit) = viewModelScope.launch {
        try { action(); refreshReminders() } catch (e: CancellationException) { throw e } catch (_: Exception) { notice.value = ui(R.string.ui_save_duplicate_error) }
    }
    fun resources(subjectId: String) = dao.resources(subjectId)
    fun saveResource(resource: SubjectResourceEntity, existing: Boolean) = change {
        if (existing) dao.updateResource(resource.copy(updatedAt = System.currentTimeMillis())) else dao.insertResource(resource)
    }
    fun deleteResource(id: String) = change { dao.deleteResource(id) }
    fun editSubject(value: SubjectEntity) = change { dao.updateSubject(value.copy(name = value.name.trim())) }
    fun editChapter(value: ChapterEntity) = change { dao.updateChapter(value.copy(name = value.name.trim())) }
    private suspend fun saveTargets(card: CardEntity, targets: List<PronunciationTarget>) {
        val language = dao.lessonById(card.lessonId)?.learningLanguage
        val valid = PronunciationResolver.validate(card.front, card.back, targets, language)
        dao.replacePronunciationTargets(card.id, valid.targets.map { it.entity(card.id) })
    }
    fun editCard(value: CardEntity, targets: List<PronunciationTarget>) = change {
        database.withTransaction { dao.updateCard(value.copy(updatedAt = System.currentTimeMillis())); saveTargets(value, targets) }
    }
    fun duplicateCard(value: CardEntity) = change {
        database.withTransaction {
            val copy = value.copy(id = java.util.UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
            dao.insertCard(copy); dao.saveState(ReviewStateEntity(cardId = copy.id))
            saveTargets(copy, dao.targetsForCard(value.id).map { it.target() })
        }
    }
    fun editTag(value: TagEntity) = change { dao.updateTag(value.copy(name = value.name.trim().removePrefix("#"))) }
    fun editSchedule(value: ScheduleBlockEntity) = change { dao.saveSchedule(value) }
    fun editLesson(id: String, title: String, summary: String?, chapterId: String?, tags: List<String>) = change {
        database.withTransaction {
            val old = requireNotNull(dao.lessonById(id))
            dao.updateLesson(old.copy(title = title.trim(), summary = summary, chapterId = chapterId, updatedAt = System.currentTimeMillis()))
            dao.deleteLessonTagLinks(id)
            attachTags(id, tags)
        }
    }
    fun loadLesson(id: String, loaded: (LessonEntity) -> Unit) = viewModelScope.launch { dao.lessonById(id)?.let(loaded) }
    fun importDraft(draft: ImportDraft, onComplete: () -> Unit, subjectId: String? = null, lessonId: String? = null) = change {
        database.withTransaction {
        val subject = if (subjectId != null) requireNotNull(dao.subjectById(subjectId)) else dao.allSubjects().firstOrNull { normalizedName(it.name) == normalizedName(draft.subject) } ?: SubjectEntity(name = draft.subject.trim()).also { dao.insertSubject(it) }
        val chapter = (if (lessonId == null) draft.chapter else null)?.takeIf { it.isNotBlank() }?.let { dao.findChapter(subject.id, it) ?: ChapterEntity(subjectId = subject.id, name = it).also { c -> dao.insertChapter(c) } }
        val lesson = (if (lessonId != null) requireNotNull(dao.lessonById(lessonId)).also { require(it.subjectId == subject.id) } else dao.findLessonInChapter(subject.id, chapter?.id, draft.lesson)) ?: LessonEntity(subjectId = subject.id, chapterId = chapter?.id, title = draft.lesson, summary = draft.summary, contentType = draft.contentType, learningLanguage = draft.learningLanguage).also { dao.insertLesson(it) }
        if (lesson.learningLanguage == null && draft.learningLanguage != null) {
            dao.updateLesson(lesson.copy(contentType = draft.contentType, learningLanguage = draft.learningLanguage))
        }
        attachTags(lesson.id, draft.tags)
        val knownFronts = dao.cardFronts(lesson.id).map(::normalizedName).toMutableSet()
        draft.cards.filter { it.included && !it.duplicate }.forEach { imported -> if (knownFronts.add(normalizedName(imported.front))) { val card = CardEntity(lessonId = lesson.id, type = imported.type, front = imported.front, back = imported.back, hint = imported.hint, sourceReference = imported.source); dao.insertCard(card); dao.saveState(ReviewStateEntity(cardId = card.id)); saveTargets(card, imported.pronunciationTargets.map { target -> target.copy(language = target.language ?: draft.learningLanguage?.takeIf { it != (lesson.learningLanguage ?: draft.learningLanguage) }) }) } }
        }
        onComplete()
    }
}
