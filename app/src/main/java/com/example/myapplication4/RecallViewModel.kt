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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.example.myapplication4.notifications.*
import java.util.Calendar
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.YearMonth

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecallViewModel @JvmOverloads constructor(app: Application, private val database: RecallDatabase = RecallDatabase.create(app)) : AndroidViewModel(app) {
    val pronunciation = com.example.myapplication4.pronunciation.PronunciationManager(app)
    override fun onCleared() { pronunciation.close(); database.close(); super.onCleared() }
    fun pronunciations(cardId: String) = dao.pronunciationTargets(cardId).map { rows -> rows.map { it.target() } }
    suspend fun loadPronunciations(cardId: String) = dao.targetsForCard(cardId).map { it.target() }
    private val dao = database.dao()
    private val preferences = UserPreferences(app)
    val introductionSeen: StateFlow<Boolean?> = preferences.introductionSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun completeIntroduction() = viewModelScope.launch {
        try { preferences.markIntroductionSeen() }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            notice.value = "Could not save your preference. Please try again."
        }
    }
    private val scheduler: ReviewScheduler = FsrsScheduler()
    // Kept across Activity recreation without putting full card text in Android's
    // size-limited saved-state Bundle. Process recreation returns safely to Today.
    internal var activeReviewCards: List<CardWithLesson>? = null
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
    val dueCount = clock.flatMapLatest { dao.dueCount(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val todayReviews = clock.map { todayStart() }.distinctUntilChanged().flatMapLatest { dao.reviewCountSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val localDays = clock.map { ActivityPeriod.current() }.distinctUntilChanged()
    val studyActivity = localDays.flatMapLatest { period ->
        dao.studyActivity(studyActivityQuery(period)).map { rows ->
            StudyActivity(period, rows.associate { LocalDate.ofEpochDay(it.epochDay) to it.reviewCount })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyActivity(ActivityPeriod.current()))
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
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
    private fun loadReview(load: suspend () -> List<CardWithLesson>, onLoaded: (List<CardWithLesson>) -> Unit) = viewModelScope.launch {
        if (reviewLoading.value) return@launch
        reviewLoading.value = true
        try { onLoaded(withContext(Dispatchers.Default) { applyNewCardLimit(load()) }) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { notice.value = "Could not load the review. Please try again." }
        finally { reviewLoading.value = false }
    }
    fun reviewDue(onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({ dao.dueCards(System.currentTimeMillis()) }, onLoaded)
    fun reviewSubject(subjectId: String, onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({ dao.dueCards(System.currentTimeMillis(), subjectId) }, onLoaded)
    fun reviewLesson(lessonId: String, onLoaded: (List<CardWithLesson>) -> Unit) = loadReview({ dao.lessonCards(lessonId) }, onLoaded)
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
        val saved = dao.commitReviewIfCurrent(card.reviewState(), result.state, log)
        if (!saved) notice.value = "This card changed during the review. Reopen the review to use its latest state."
        onSaved(saved)
        if (saved) queueReminderRefresh()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            notice.value = "Your answer could not be saved. Please try again."
            onSaved(false)
        }
    }
    private fun CardWithLesson.reviewState() = ReviewStateEntity(id, state, dueAt, lastReviewedAt, stability, difficulty, scheduledDays, reps, lapses)
    fun setSuspended(id: String, suspended: Boolean) = viewModelScope.launch { dao.setSuspended(id, suspended, System.currentTimeMillis()); refreshReminders() }
    fun deleteCard(id: String) = viewModelScope.launch { dao.deleteCard(id) }
    fun saveSchedule(name: String, type: String, start: Int, end: Int, days: Set<Int>) = viewModelScope.launch { if (name.isNotBlank() && days.isNotEmpty()) dao.saveSchedule(ScheduleBlockEntity(name = name.trim(), type = type, startMinute = start, endMinute = end, days = days.sorted().joinToString(","))); refreshReminders() }
    fun deleteSchedule(id: String) = viewModelScope.launch { dao.deleteSchedule(id); refreshReminders() }
    fun deleteSubject(id: String, onComplete: () -> Unit = {}) = viewModelScope.launch { dao.deleteSubject(id); onComplete() }
    fun deleteChapter(id: String) = viewModelScope.launch { dao.deleteChapter(id) }
    fun deleteLesson(id: String, onComplete: () -> Unit = {}) = viewModelScope.launch { dao.deleteLesson(id); onComplete() }
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
    fun exportBackup(onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        onResult(withContext(Dispatchers.Default) { runCatching { RecallBackupCodec.encode(dao.backup(), settings.value) } })
    }
    fun importBackup(raw: String, onResult: (String) -> Unit) = viewModelScope.launch {
        when (val parsed = withContext(Dispatchers.Default) { RecallBackupCodec.decode(raw) }) {
            is BackupResult.Failure -> onResult(parsed.message)
            is BackupResult.Success -> runCatching {
                dao.mergeBackup(parsed.data)
                preferences.restore(parsed.settings)
                refreshReminders()
            }.fold({ onResult("Backup restored. Existing data was kept.") }, { onResult("The backup could not be restored safely.") })
        }
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
        result(ReminderNotifications.post(getApplication(), dao.reminderDueCount(System.currentTimeMillis()), test = true) ?: "Test notification posted. Check your notification shade.")
    }
    val archiveBusy = MutableStateFlow(false)
    val archiveStatus = MutableStateFlow<String?>(null)
    fun exportAllData(uri: android.net.Uri) = viewModelScope.launch {
        if (archiveBusy.value) return@launch
        archiveBusy.value = true; archiveStatus.value = "Preparing all data and attachments…"
        val context = getApplication<Application>()
        var staged: java.io.File? = null
        try {
            withContext(Dispatchers.IO) {
                val file = java.io.File.createTempFile("recall-export-", ".zip", context.cacheDir)
                staged = file
                FullBackupArchive.write(file.outputStream(), dao.backup(), preferences.settings.first()) { source ->
                    context.contentResolver.openInputStream(android.net.Uri.parse(source)) ?: error("File unavailable")
                }
                archiveStatus.value = "Writing complete archive…"
                context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot write destination")
            }
            archiveStatus.value = "All data exported, including attached files and materials."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { archiveStatus.value = "Export failed; no complete backup was saved. " + e.message }
        finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { staged?.delete() }; archiveBusy.value = false }
    }
    fun restoreAllData(uri: android.net.Uri) = viewModelScope.launch {
        if (archiveBusy.value) return@launch
        archiveBusy.value = true; archiveStatus.value = "Validating archive and files…"
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
                dao.mergeBackup(mapped)
                committed = true
                // Merging keeps existing records. Do not retain extra attachment copies for ignored IDs.
                val retainedUris = dao.allResources().mapNotNull { it.uri }.toSet()
                mapped.resources.forEach { resource ->
                    if (resource.uri !in retainedUris) restored.files[resource.id]?.delete()
                }
                if (folder.listFiles().isNullOrEmpty()) folder.delete()
                preferences.restore(restored.settings)
            }
            refreshReminders()
            archiveStatus.value = "Full backup restored. Existing records were kept; settings restored."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { archiveStatus.value = if (committed) "Study data restored, but settings could not be restored. " + e.message else "Restore failed; existing data was not changed. " + e.message }
        finally {
            if (!committed) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { directory?.let { folder -> folder.listFiles().orEmpty().forEach { it.delete() }; folder.delete() } }
            archiveBusy.value = false
        }
    }
    val notice = MutableStateFlow<String?>(null)
    private fun change(action: suspend () -> Unit) = viewModelScope.launch {
        try { action(); refreshReminders() } catch (e: CancellationException) { throw e } catch (_: Exception) { notice.value = "Could not save. Check for duplicate names and try again." }
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
