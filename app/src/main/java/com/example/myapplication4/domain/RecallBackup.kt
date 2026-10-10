package com.example.myapplication4.domain

import com.example.myapplication4.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader
import java.io.Writer

sealed interface BackupResult {
    data class Success(val data: BackupData, val settings: UserSettings) : BackupResult
    data class Failure(val message: String) : BackupResult
}

object RecallBackupCodec {
    fun encode(data: BackupData, settings: UserSettings): String = JSONObject().apply {
        put("format", "recall-backup")
        put("version", 3)
        put("exported_at", System.currentTimeMillis())
        put("subjects", array(data.subjects) { subject(it) })
        put("chapters", array(data.chapters) { chapter(it) })
        put("lessons", array(data.lessons) { lesson(it) })
        put("cards", array(data.cards) { card(it) })
        put("pronunciation_targets", array(data.pronunciationTargets) { JSONObject().put("id", it.id).put("card_id", it.cardId).put("side", it.side).put("text", it.text).putNullable("language", it.language).put("occurrence", it.occurrenceIndex) })
        put("tags", array(data.tags) { JSONObject().put("id", it.id).put("name", it.name) })
        put("lesson_tags", array(data.lessonTags) { JSONObject().put("lesson_id", it.lessonId).put("tag_id", it.tagId) })
        put("review_states", array(data.states) { state(it) })
        put("review_logs", array(data.logs) { log(it) })
        put("schedules", array(data.schedules) { schedule(it) })
        put("import_history", array(data.imports) { imported(it) })
        put("resources", array(data.resources) { JSONObject().put("id", it.id).put("subject_id", it.subjectId).put("title", it.title).put("kind", it.kind).put("note", it.note).putNullable("uri", it.uri).putNullable("mime_type", it.mimeType).put("created_at", it.createdAt).put("updated_at", it.updatedAt) })
        put("settings", JSONObject()
            .put("desired_retention", settings.desiredRetention)
            .put("new_card_limit", settings.newCardLimit)
            .put("reminders_enabled", settings.remindersEnabled)
            .put("study_window_reminder", settings.studyWindowReminder)
            .putNullable("paused_until", settings.pausedUntil)
            .put("lesson_pauses", JSONArray(LessonPauseCodec.encode(settings.lessonPauses)))
            .put("container_pauses", JSONArray(ReviewPauseCodec.encode(settings.reviewPauses.filter { it.scope != ReviewPauseScope.LESSON })))
            .put("theme_mode", settings.themeMode)
            .put("language", settings.language)
            .put("dynamic_color", settings.dynamicColor).put("speech_rate", settings.speechRate).put("debug_mode", settings.debugMode))
    }.toString(2)

    fun decode(raw: String): BackupResult = if (raw.length <= 50_000_000) read(raw.reader()) else BackupResult.Failure("Backup exceeds size limit")

    private fun decodeDocument(raw: String, streamedLogs: List<ReviewLogEntity>?): BackupResult = try {
        require(raw.length <= 50_000_000)
        val root = JSONObject(raw)
        if (root.optString("format") != "recall-backup" || root.optInt("version") !in 1..3) {
            error("Unsupported backup")
        }
        fun arr(name: String) = root.optJSONArray(name) ?: JSONArray()
        val subjects = objects(arr("subjects")) { SubjectEntity(req(it, "id"), req(it, "name"), it.optString("icon", "book"), it.optString("accent", "blue"), it.optLong("created_at"), it.optInt("position"), it.optBoolean("archived")) }
        val chapters = objects(arr("chapters")) { ChapterEntity(req(it, "id"), req(it, "subject_id"), req(it, "name"), it.optInt("position"), it.optLong("created_at")) }
        val lessons = objects(arr("lessons")) { LessonEntity(req(it, "id"), req(it, "subject_id"), nullable(it, "chapter_id"), req(it, "title"), nullable(it, "summary"), nullable(it, "notes"), it.optLong("created_at"), it.optLong("updated_at"), it.optBoolean("archived"), it.optString("content_type", "general"), PronunciationResolver.languageTag(nullable(it, "learning_language"))) }
        val cards = objects(arr("cards")) { CardEntity(req(it, "id"), req(it, "lesson_id"), req(it, "type").also { type -> require(type in setOf("qa", "cloze")) }, req(it, "front"), req(it, "back"), nullable(it, "hint"), nullable(it, "source_reference"), it.optLong("created_at"), it.optLong("updated_at"), it.optBoolean("suspended")) }
        val cardMap = cards.associateBy { it.id }
        val lessonMap = lessons.associateBy { it.id }
        val targets = objects(arr("pronunciation_targets")) { PronunciationTargetEntity(req(it, "id"), req(it, "card_id"), req(it, "side"), req(it, "text"), nullable(it, "language"), it.getInt("occurrence")) }
        require(targets.map { it.id }.toSet().size == targets.size)
        targets.groupBy { it.cardId }.forEach { (id, values) ->
            val card = requireNotNull(cardMap[id])
            require(PronunciationResolver.validate(card.front, card.back, values.map { it.target() }, lessonMap[card.lessonId]?.learningLanguage).skipped == 0)
        }
        val tags = objects(arr("tags")) { TagEntity(req(it, "id"), req(it, "name")) }
        val links = objects(arr("lesson_tags")) { LessonTagEntity(req(it, "lesson_id"), req(it, "tag_id")) }
        val states = objects(arr("review_states")) { ReviewStateEntity(req(it, "card_id"), req(it, "state"), it.getLong("due_at"), nullableLong(it, "last_reviewed_at"), it.getDouble("stability"), it.getDouble("difficulty"), it.optInt("scheduled_days"), it.optInt("reps"), it.optInt("lapses")) }
        val logs = streamedLogs ?: objects(arr("review_logs"), ::readLog)
        val schedules = objects(arr("schedules")) { ScheduleBlockEntity(req(it, "id"), req(it, "name"), req(it, "type").also { type -> require(type in setOf("quiet", "window")) }, it.getInt("start_minute").also { value -> require(value in 0..1439) }, it.getInt("end_minute").also { value -> require(value in 0..1439) }, req(it, "days"), it.optBoolean("enabled", true), nullable(it, "preferred_filter")) }
        val imports = objects(arr("import_history")) { ImportRecordEntity(req(it, "id"), req(it, "fingerprint"), it.getLong("imported_at"), it.optString("source", "backup")) }
        require(subjects.map { it.id }.toSet().size == subjects.size)
        require(cards.map { it.id }.toSet().size == cards.size)
        val resources = objects(arr("resources")) { SubjectResourceEntity(req(it, "id"), req(it, "subject_id"), req(it, "title"), req(it, "kind"), it.optString("note"), nullable(it, "uri"), nullable(it, "mime_type"), it.getLong("created_at"), it.getLong("updated_at")) }
        val preferences = root.optJSONObject("settings") ?: JSONObject()
        require(!preferences.has("lesson_pauses") || preferences.get("lesson_pauses") is JSONArray)
        require(!preferences.has("container_pauses") || preferences.get("container_pauses") is JSONArray)
        val pauses = LessonPauseCodec.decode((preferences.optJSONArray("lesson_pauses") ?: JSONArray()).toString()).map { it.asReviewPause() } +
            ReviewPauseCodec.decode((preferences.optJSONArray("container_pauses") ?: JSONArray()).toString()).also { require(it.none { pause -> pause.scope == ReviewPauseScope.LESSON }) }
        require(pauses.all { pause -> when (pause.scope) {
            ReviewPauseScope.SUBJECT -> subjects.any { it.id == pause.targetId }
            ReviewPauseScope.CHAPTER -> chapters.any { it.id == pause.targetId }
            ReviewPauseScope.LESSON -> pause.targetId in lessonMap
        } })
        val settings = UserSettings(
            desiredRetention = preferences.optDouble("desired_retention", .90).coerceIn(.85, .95),
            newCardLimit = preferences.optInt("new_card_limit", 20).coerceIn(0, 1000),
            remindersEnabled = preferences.optBoolean("reminders_enabled", false),
            studyWindowReminder = preferences.optBoolean("study_window_reminder", true),
            pausedUntil = nullableLong(preferences, "paused_until"),
            reviewPauses = pauses,
            themeMode = preferences.optString("theme_mode", "system").takeIf { it in setOf("system", "light", "dark") } ?: "system",
            language = preferences.optString("language", "system").takeIf { it in setOf("system", "en", "ar", "es", "fr", "de") } ?: "system",
            dynamicColor = preferences.optBoolean("dynamic_color", false),
            debugMode = preferences.optBoolean("debug_mode", false),
            speechRate = preferences.optDouble("speech_rate", 1.0).toFloat().takeIf { it in .75f..1.25f } ?: 1f,
        )
        val data = BackupData(subjects, chapters, lessons, cards, tags, links, states, logs, schedules, imports, resources, targets)
        validateBackupData(data)
        BackupResult.Success(data, settings)
    } catch (_: Exception) {
        BackupResult.Failure("The selected file is damaged or is not a valid Recall backup.")
    }

    fun write(writer: Writer, data: BackupData, settings: UserSettings) {
        val metadata = JSONObject(encode(data.copy(logs = emptyList()), settings))
        metadata.remove("review_logs")
        val header = metadata.toString()
        writer.write(header.dropLast(1))
        writer.write(",\"review_logs\":[")
        data.logs.forEachIndexed { index, row ->
            if (index > 0) writer.write(",")
            writer.write(log(row).toString())
        }
        writer.write("]}")
        writer.flush()
    }

    fun read(reader: Reader): BackupResult = try {
        val logs = ArrayList<ReviewLogEntity>()
        var consumed = 0L
        val bounded = object : java.io.FilterReader(reader) {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count > 0) { consumed += count; require(consumed <= 50_000_000) }
                return count
            }
            override fun read(): Int {
                val c = super.read()
                if (c >= 0) { consumed++; require(consumed <= 50_000_000) }
                return c
            }
        }
        val metadata = BackupEnvelopeReader(bounded).read { logs.add(readLog(it)) }
        decodeDocument(metadata, logs)
    } catch (_: Exception) { BackupResult.Failure("Invalid backup document") }

    private fun readLog(it: JSONObject) = ReviewLogEntity(
        id = req(it, "id"), cardId = req(it, "card_id"), reviewedAt = it.getLong("reviewed_at"),
        rating = it.getInt("rating"), previousInterval = it.optInt("previous_interval"), nextInterval = it.optInt("next_interval"),
        previousStability = it.getDouble("previous_stability"), newStability = it.getDouble("new_stability"), durationMillis = it.optLong("duration_millis"),
        previousDueAt = it.optLong("previous_due_at"), nextDueAt = it.optLong("next_due_at"), elapsedDays = it.optDouble("elapsed_days", 0.0),
        previousDifficulty = it.optDouble("previous_difficulty", 5.0), newDifficulty = it.optDouble("new_difficulty", 5.0),
        previousState = it.optString("previous_state", "new"), newState = it.optString("new_state", "new"), reps = it.optInt("reps"), lapses = it.optInt("lapses"),
    )

    private fun subject(v: SubjectEntity) = JSONObject().put("id", v.id).put("name", v.name).put("icon", v.icon).put("accent", v.accent).put("created_at", v.createdAt).put("position", v.position).put("archived", v.archived)
    private fun chapter(v: ChapterEntity) = JSONObject().put("id", v.id).put("subject_id", v.subjectId).put("name", v.name).put("position", v.position).put("created_at", v.createdAt)
    private fun lesson(v: LessonEntity) = JSONObject().put("id", v.id).put("subject_id", v.subjectId).putNullable("chapter_id", v.chapterId).put("title", v.title).putNullable("summary", v.summary).putNullable("notes", v.notes).put("content_type", v.contentType).putNullable("learning_language", v.learningLanguage).put("created_at", v.createdAt).put("updated_at", v.updatedAt).put("archived", v.archived)
    private fun card(v: CardEntity) = JSONObject().put("id", v.id).put("lesson_id", v.lessonId).put("type", v.type).put("front", v.front).put("back", v.back).putNullable("hint", v.hint).putNullable("source_reference", v.sourceReference).put("created_at", v.createdAt).put("updated_at", v.updatedAt).put("suspended", v.suspended)
    private fun state(v: ReviewStateEntity) = JSONObject().put("card_id", v.cardId).put("state", v.state).put("due_at", v.dueAt).putNullable("last_reviewed_at", v.lastReviewedAt).put("stability", v.stability).put("difficulty", v.difficulty).put("scheduled_days", v.scheduledDays).put("reps", v.reps).put("lapses", v.lapses)
    private fun log(v: ReviewLogEntity) = JSONObject().put("id", v.id).put("card_id", v.cardId).put("reviewed_at", v.reviewedAt).put("rating", v.rating).put("previous_interval", v.previousInterval).put("next_interval", v.nextInterval).put("previous_stability", v.previousStability).put("new_stability", v.newStability).put("duration_millis", v.durationMillis).put("previous_due_at", v.previousDueAt).put("next_due_at", v.nextDueAt).put("elapsed_days", v.elapsedDays).put("previous_difficulty", v.previousDifficulty).put("new_difficulty", v.newDifficulty).put("previous_state", v.previousState).put("new_state", v.newState).put("reps", v.reps).put("lapses", v.lapses)
    private fun schedule(v: ScheduleBlockEntity) = JSONObject().put("id", v.id).put("name", v.name).put("type", v.type).put("start_minute", v.startMinute).put("end_minute", v.endMinute).put("days", v.days).put("enabled", v.enabled).putNullable("preferred_filter", v.preferredFilter)
    private fun imported(v: ImportRecordEntity) = JSONObject().put("id", v.id).put("fingerprint", v.fingerprint).put("imported_at", v.importedAt).put("source", v.source)
    private fun <T> array(data: List<T>, transform: (T) -> JSONObject) = JSONArray().apply { data.forEach { put(transform(it)) } }
    private fun <T> objects(data: JSONArray, transform: (JSONObject) -> T) = (0 until data.length()).map { transform(data.getJSONObject(it)) }
    private fun req(value: JSONObject, key: String) = (value.get(key) as? String)?.takeIf { it.isNotBlank() && it.length <= 1_000_000 } ?: error("Missing or oversized $key")
    private fun nullable(value: JSONObject, key: String): String? {
        if (!value.has(key) || value.isNull(key)) return null
        return (value.get(key) as? String)?.also { require(it.length <= 1_000_000) } ?: error("Invalid $key")
    }
    private fun nullableLong(value: JSONObject, key: String) = if (!value.has(key) || value.isNull(key)) null else value.getLong(key)
    private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
}
