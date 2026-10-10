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
