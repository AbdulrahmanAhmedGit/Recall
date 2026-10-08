package com.example.myapplication4.domain

import com.example.myapplication4.data.BackupData

/** Validate the complete graph before either JSON or ZIP restoration mutates storage. */
fun validateBackupData(data: BackupData) {
    fun <T> unique(rows: List<T>, key: (T) -> Any) = require(rows.map(key).toSet().size == rows.size) { "Duplicate backup IDs" }
    unique(data.subjects) { it.id }; unique(data.chapters) { it.id }; unique(data.lessons) { it.id }
    unique(data.cards) { it.id }; unique(data.tags) { it.id }; unique(data.states) { it.cardId }
    unique(data.logs) { it.id }; unique(data.resources) { it.id }; unique(data.schedules) { it.id }
    unique(data.imports) { it.id }; unique(data.pronunciationTargets) { it.id }
    unique(data.lessonTags) { it.lessonId to it.tagId }
    unique(data.subjects) { it.name }; unique(data.tags) { it.name }
    val subjects = data.subjects.map { it.id }.toSet()
    val chapters = data.chapters.associateBy { it.id }
    val lessons = data.lessons.map { it.id }.toSet()
    val cards = data.cards.map { it.id }.toSet()
    val tags = data.tags.map { it.id }.toSet()
    require(data.chapters.all { it.subjectId in subjects })
    require(data.lessons.all { it.subjectId in subjects && (it.chapterId == null || chapters[it.chapterId]?.subjectId == it.subjectId) })
    require(data.cards.all { it.lessonId in lessons })
    require(data.resources.all { it.subjectId in subjects })
    require(data.lessonTags.all { it.lessonId in lessons && it.tagId in tags })
    require(data.pronunciationTargets.all { it.cardId in cards })
    require(data.states.all { it.cardId in cards && it.stability.isFinite() && it.difficulty.isFinite() && it.reps >= 0 && it.lapses >= 0 && it.scheduledDays >= 0 })
    // Zero-valued legacy audit fields remain valid; never fabricate their missing history.
    require(data.logs.all { it.cardId in cards && it.rating in 1..4 && it.durationMillis >= 0 &&
        it.previousStability.isFinite() && it.newStability.isFinite() && it.elapsedDays.isFinite() &&
        it.previousDifficulty.isFinite() && it.newDifficulty.isFinite() })
    require(data.schedules.all { block -> block.days.split(',').all { it.toIntOrNull() in 1..7 } })
}
