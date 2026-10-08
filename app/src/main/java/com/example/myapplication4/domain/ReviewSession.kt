package com.example.myapplication4.domain

/** Session-only state: completed answers live in Room, unfinished questions do not. */
data class ReviewSessionProgress(
    val index: Int = 0,
    val revealed: Boolean = false,
    val skipped: Int = 0,
    val counts: List<Int> = List(Rating.entries.size) { 0 },
    val saving: Boolean = false,
)

/** Monotonic foreground duration, unaffected by wall-clock changes or time spent away. */
class ReviewTimer {
    private var cardId: String? = null
    private var started: Long? = null
    private var accumulated = 0L
    fun begin(id: String, now: Long) {
        if (cardId != id) { cardId = id; accumulated = 0; started = null }
        resume(now)
    }
    fun resume(now: Long) { if (started == null) started = now }
    fun pause(now: Long) { started?.let { accumulated += (now - it).coerceAtLeast(0) }; started = null }
    fun duration(now: Long): Long = accumulated + (started?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

object BacklogPolicy {
    const val threshold = 60
    const val batchSize = 20
    fun isBacklog(due: Int) = due >= threshold
}

/** A changed interval must be shown before the learner confirms it. */
fun sameReviewIntervals(shown: Map<Rating, ScheduleResult>, fresh: Map<Rating, ScheduleResult>): Boolean =
    Rating.entries.all { shown.getValue(it).intervalMillis == fresh.getValue(it).intervalMillis }
