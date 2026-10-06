package com.example.myapplication4.domain

import com.example.myapplication4.data.ReviewStateEntity
import com.example.myapplication4.data.ScheduleBlockEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }
data class ScheduleResult(
    val rating: Rating,
    val state: ReviewStateEntity,
    val reviewedAt: Long,
    val previousDueAt: Long,
    val previousState: String,
    val previousDays: Int,
    val nextDays: Int,
    val elapsedDays: Double,
) {
    val intervalMillis: Long get() = state.dueAt - reviewedAt
}

interface ReviewScheduler {
    /** All button previews are calculated together so interval ordering is deterministic. */
    fun preview(state: ReviewStateEntity, now: Long, retention: Double = .90): Map<Rating, ScheduleResult>
    fun schedule(state: ReviewStateEntity, rating: Rating, now: Long, retention: Double = .90): ScheduleResult =
        requireNotNull(preview(state, now, retention)[rating])
}

/**
 * FSRS-6 memory model using the official 21 default parameters. Recall deliberately
 * keeps one product-level relearning step (Again = 10 minutes); graduated intervals
 * and every stability/difficulty transition use the FSRS-6 equations.
 */
class FsrsScheduler : ReviewScheduler {
    override fun preview(state: ReviewStateEntity, now: Long, retention: Double): Map<Rating, ScheduleResult> {
        val requestedRetention = retention.takeIf { it.isFinite() }?.coerceIn(.01, .99) ?: .90
        val exactElapsed = state.lastReviewedAt?.let { max(0.0, (now - it).toDouble() / DAY_MILLIS) } ?: 0.0
        val modelElapsed = state.lastReviewedAt?.let {
            max(0L, Math.floorDiv(now, DAY_MILLIS) - Math.floorDiv(it, DAY_MILLIS)).toDouble()
        } ?: 0.0
        // Repetitions are authoritative for legacy rows; a missing timestamp must not
        // erase an already learned card's memory state.
        val isNew = state.reps <= 0
        val memories = Rating.entries.associateWith { rating ->
            if (isNew) MODEL.initial(rating) else MODEL.next(
                FsrsMemory(
                    stability = finiteIn(state.stability, STABILITY_MIN, STABILITY_MAX, .4),
                    difficulty = finiteIn(state.difficulty, DIFFICULTY_MIN, DIFFICULTY_MAX, 5.0),
                ),
                rating,
                modelElapsed,
            )
        }
        val rawDays = Rating.entries.associateWith { rating ->
            MODEL.interval(memories.getValue(rating).stability, requestedRetention)
        }
        val hard = min(rawDays.getValue(Rating.HARD), rawDays.getValue(Rating.GOOD))
        val good = max(rawDays.getValue(Rating.GOOD), hard + 1)
        val easy = max(rawDays.getValue(Rating.EASY), good + 1)
        val orderedDays = mapOf(Rating.HARD to hard, Rating.GOOD to good, Rating.EASY to easy)

        return Rating.entries.associateWith { rating ->
            val days = if (rating == Rating.AGAIN) 0 else orderedDays.getValue(rating)
            val due = now + if (rating == Rating.AGAIN) AGAIN_MILLIS else days.toLong() * DAY_MILLIS
            val memory = memories.getValue(rating)
            val failedReview = rating == Rating.AGAIN && !isNew
            ScheduleResult(
                rating = rating,
                state = state.copy(
                    state = when {
                        rating == Rating.AGAIN && isNew -> "learning"
                        rating == Rating.AGAIN -> "relearning"
                        else -> "review"
                    },
                    dueAt = due,
                    lastReviewedAt = now,
                    stability = memory.stability,
                    difficulty = memory.difficulty,
                    scheduledDays = days,
                    reps = state.reps.coerceAtLeast(0) + 1,
                    lapses = state.lapses.coerceAtLeast(0) + if (failedReview) 1 else 0,
                ),
                reviewedAt = now,
                previousDueAt = state.dueAt,
                previousState = state.state,
                previousDays = state.scheduledDays,
                nextDays = days,
                elapsedDays = exactElapsed,
            )
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val AGAIN_MILLIS = 10 * 60_000L
        val MODEL = Fsrs6MemoryModel()
    }
}

internal data class FsrsMemory(val stability: Double, val difficulty: Double)

/** Direct Kotlin port of the FSRS-6 memory equations; intentionally UI-free. */
internal class Fsrs6MemoryModel(
    private val weights: DoubleArray = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
        0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629,
        1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    ),
) {
    private val decay = -weights[20]
    private val factor = .9.pow(1.0 / decay) - 1.0

    fun initial(rating: Rating) = FsrsMemory(
        stability = weights[rating.value - 1].coerceIn(.1, STABILITY_MAX),
        difficulty = initialDifficulty(rating).coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX),
    )

    fun next(current: FsrsMemory, rating: Rating, elapsedDays: Double): FsrsMemory {
        val stability = finiteIn(current.stability, STABILITY_MIN, STABILITY_MAX, .4)
        val difficulty = finiteIn(current.difficulty, DIFFICULTY_MIN, DIFFICULTY_MAX, 5.0)
        val nextDifficulty = nextDifficulty(difficulty, rating)
        val nextStability = if (elapsedDays <= 0.0) {
            shortTermStability(stability, rating)
        } else {
            val retrievability = forgettingCurve(elapsedDays, stability)
            if (rating == Rating.AGAIN) nextForgetStability(difficulty, stability, retrievability)
            else nextRecallStability(difficulty, stability, retrievability, rating)
        }
        return FsrsMemory(nextStability, nextDifficulty)
    }

    fun interval(stability: Double, retention: Double): Int {
        val raw = finiteIn(stability, STABILITY_MIN, STABILITY_MAX, .4) / factor *
            (retention.pow(1.0 / decay) - 1.0)
        return raw.roundToInt().coerceIn(1, STABILITY_MAX.toInt())
    }

    fun forgettingCurve(elapsedDays: Double, stability: Double): Double =
        (1 + factor * elapsedDays / stability.coerceIn(STABILITY_MIN, STABILITY_MAX)).pow(decay)

    private fun initialDifficulty(rating: Rating) = weights[4] - exp(weights[5] * (rating.value - 1)) + 1

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -weights[6] * (rating.value - 3)
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val reverted = weights[7] * initialDifficulty(Rating.EASY) + (1 - weights[7]) * damped
        return reverted.coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX)
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = exp(weights[17] * (rating.value - 3 + weights[18])) * stability.pow(-weights[19])
        if (rating.value >= Rating.HARD.value && increase < 1.0) increase = 1.0
        return (stability * increase).coerceIn(STABILITY_MIN, STABILITY_MAX)
    }

    private fun nextRecallStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) weights[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) weights[16] else 1.0
        return (stability * (1 + exp(weights[8]) * (11 - difficulty) * stability.pow(-weights[9]) *
            (exp((1 - retrievability) * weights[10]) - 1) * hardPenalty * easyBonus))
            .coerceIn(STABILITY_MIN, STABILITY_MAX)
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val forgotten = weights[11] * difficulty.pow(-weights[12]) *
            ((stability + 1).pow(weights[13]) - 1) * exp((1 - retrievability) * weights[14])
        val ceiling = stability / exp(weights[17] * weights[18])
        return min(forgotten, ceiling).coerceIn(STABILITY_MIN, STABILITY_MAX)
    }
}

private const val STABILITY_MIN = .001
private const val STABILITY_MAX = 36_500.0
private const val DIFFICULTY_MIN = 1.0
private const val DIFFICULTY_MAX = 10.0
private fun finiteIn(value: Double, min: Double, max: Double, fallback: Double) =
    if (value.isFinite()) value.coerceIn(min, max) else fallback

fun formatReviewInterval(milliseconds: Long): String {
    val value = milliseconds.coerceAtLeast(0)
    val seconds = max(1L, (value + 500L) / 1_000L)
    if (seconds < 60) return "${seconds}s"
    val minutes = max(1L, (value + 30_000L) / 60_000L)
    if (minutes < 60) return "${minutes}m"
    val hours = max(1L, (value + 1_800_000L) / 3_600_000L)
    if (hours < 24) return "${hours}h"
    val days = max(1L, (value + 43_200_000L) / 86_400_000L)
    if (days < 14) return "${days}d"
    if (days < 60) return "${max(2L, (days + 3) / 7)}w"
    return "${max(2L, (days + 15) / 30)}mo"
}

data class NotificationDecision(val deliverAt: Long?, val reason: String)
class NotificationPolicy(private val zone: ZoneId = ZoneId.systemDefault()) {
    fun nextAllowed(now: Long, blocks: List<ScheduleBlockEntity>, pausedUntil: Long?, preferWindows: Boolean): NotificationDecision {
        val start = maxOf(now, pausedUntil ?: now)
        val windows = blocks.filter { it.enabled && it.type == "window" }
        var fallback: Long? = null
        repeat(8 * 24 * 60) { offset ->
            val candidate = start + offset * 60_000L
            val local = Instant.ofEpochMilli(candidate).atZone(zone)
            val minute = local.hour * 60 + local.minute
            if (blocks.any { it.enabled && it.type == "quiet" && active(it, local.dayOfWeek.value, minute) }) return@repeat
            val inWindow = windows.any { active(it, local.dayOfWeek.value, minute) }
            val reasonable = local.hour in 7..21 || inWindow
            if (!reasonable) return@repeat
            if (fallback == null) fallback = candidate
            if (!preferWindows || windows.isEmpty() || inWindow) {
                if (candidate - start <= 24 * 60 * 60_000L || fallback == candidate) return NotificationDecision(candidate, if (candidate == now) "Allowed" else "Waiting for an allowed study time")
                return NotificationDecision(fallback, "No study window soon; next allowed daytime")
            }
            if (offset >= 24 * 60) return NotificationDecision(fallback, "No study window soon; next allowed daytime")
        }
        return NotificationDecision(fallback, if (fallback == null) "Quiet times cover all available times" else "Next allowed daytime")
    }

    fun startingWindow(now: Long, blocks: List<ScheduleBlockEntity>): String? {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        return blocks.firstNotNullOfOrNull { block ->
            if (!block.enabled || block.type != "window") return@firstNotNullOfOrNull null
            val minute = local.hour * 60 + local.minute
            if (!active(block, local.dayOfWeek.value, minute)) return@firstNotNullOfOrNull null
            val elapsed = (minute - block.startMinute + 1440) % 1440
            if (elapsed >= 30) return@firstNotNullOfOrNull null
            val date = if (minute < block.startMinute) local.toLocalDate().minusDays(1) else local.toLocalDate()
            block.id + ":" + date
        }
    }

    private fun active(block: ScheduleBlockEntity, day: Int, minute: Int): Boolean {
        val days = block.days.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        if (block.startMinute == block.endMinute) return day in days
        if (block.startMinute < block.endMinute) return day in days && minute in block.startMinute until block.endMinute
        val previous = if (day == 1) 7 else day - 1
        return (day in days && minute >= block.startMinute) || (previous in days && minute < block.endMinute)
    }
}

fun reminderDue(now: Long, lastSent: Long?, windowKey: String?, lastWindowKey: String?, windowReminders: Boolean, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (lastSent == null) return true
    if (lastSent > now) return false
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val previousDay = Instant.ofEpochMilli(lastSent).atZone(zone).toLocalDate()
    if (today != previousDay && now - lastSent >= 4 * 60 * 60_000L) return true
    return windowReminders && windowKey != null && windowKey != lastWindowKey && now - lastSent >= 4 * 60 * 60_000L
}
