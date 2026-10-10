package com.example.myapplication4.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

data class LessonReviewPause(val lessonId: String, val startAt: Long, val endAt: Long) {
    init { require(lessonId.isNotBlank() && lessonId.length <= 128 && startAt >= 0 && endAt > startAt) }
    fun activeAt(now: Long) = now >= startAt && now < endAt
}

fun List<LessonReviewPause>.pausedLessonIds(now: Long): List<String> = filter { it.activeAt(now) }.map { it.lessonId }.distinct()

fun lessonPauseDays(lessonId: String, first: LocalDate, last: LocalDate, zone: ZoneId): LessonReviewPause {
    require(!last.isBefore(first))
    return LessonReviewPause(lessonId, first.atStartOfDay(zone).toInstant().toEpochMilli(), last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
}

object LessonPauseCodec {
    fun encode(items: List<LessonReviewPause>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("lesson_id", it.lessonId).put("start_at", it.startAt).put("end_at", it.endAt)) }
    }.toString()
    fun decode(raw: String): List<LessonReviewPause> {
        require(raw.length <= 2_000_000)
        val array = JSONArray(raw)
        require(array.length() <= 10_000)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            LessonReviewPause(it.getString("lesson_id"), it.getLong("start_at"), it.getLong("end_at"))
        } }.also { require(it.map { pause -> pause.lessonId }.distinct().size == it.size) }
    }
    fun readPreference(raw: String?): List<LessonReviewPause> = if (raw == null) emptyList() else runCatching { decode(raw) }.getOrDefault(emptyList())
}

data class ReviewFocus(val subjectId: String, val chapterId: String? = null, val lessonId: String? = null)

enum class ReviewPauseScope { SUBJECT, CHAPTER, LESSON }

data class ReviewPause(val scope: ReviewPauseScope, val targetId: String, val startAt: Long, val endAt: Long) {
    init { require(targetId.isNotBlank() && targetId.length <= 128 && startAt >= 0 && endAt > startAt) }
    val key get() = scope to targetId
    fun activeAt(now: Long) = now >= startAt && now < endAt
}

data class PauseLessonScope(val id: String, val subjectId: String, val chapterId: String?)

fun LessonReviewPause.asReviewPause() = ReviewPause(ReviewPauseScope.LESSON, lessonId, startAt, endAt)

fun List<ReviewPause>.excludedLessonIds(now: Long, lessons: List<PauseLessonScope>): List<String> {
    val active = filter { it.activeAt(now) }
    val subjects = active.filter { it.scope == ReviewPauseScope.SUBJECT }.map { it.targetId }.toSet()
    val chapters = active.filter { it.scope == ReviewPauseScope.CHAPTER }.map { it.targetId }.toSet()
    val direct = active.filter { it.scope == ReviewPauseScope.LESSON }.map { it.targetId }.toSet()
    return lessons.filter { it.id in direct || it.subjectId in subjects || it.chapterId in chapters }.map { it.id }
}

fun reviewPauseDays(scope: ReviewPauseScope, targetId: String, first: LocalDate, last: LocalDate, zone: ZoneId): ReviewPause {
    val dates = lessonPauseDays(targetId, first, last, zone)
    return ReviewPause(scope, targetId, dates.startAt, dates.endAt)
}

object ReviewPauseCodec {
    fun encode(items: List<ReviewPause>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("scope", it.scope.name.lowercase()).put("target_id", it.targetId).put("start_at", it.startAt).put("end_at", it.endAt)) }
    }.toString()
    fun decode(raw: String): List<ReviewPause> {
        require(raw.length <= 2_000_000)
        val array = JSONArray(raw)
        require(array.length() <= 10_000)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            ReviewPause(ReviewPauseScope.valueOf(it.getString("scope").uppercase(java.util.Locale.ROOT)), it.getString("target_id"), it.getLong("start_at"), it.getLong("end_at"))
        } }.also { require(it.map(ReviewPause::key).distinct().size == it.size) }
    }
    fun readPreference(raw: String?): List<ReviewPause> = if (raw == null) emptyList() else runCatching { decode(raw) }.getOrDefault(emptyList())
}
