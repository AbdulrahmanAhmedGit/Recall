package com.example.myapplication4.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reporting conventions only. These values never enter the scheduler. */
object InsightsPolicy {
    const val matureStabilityDays = 21.0
    const val observationDays = 30L
    const val historyDays = 90L
    const val minimumEvents = 30
    const val minimumCards = 10
}

enum class MemoryCategory { NEW, LEARNING, MATURE }

fun memoryCategory(reps: Int, state: String, stability: Double): MemoryCategory = when {
    reps <= 0 -> MemoryCategory.NEW
    state == "review" && stability.isFinite() && stability >= InsightsPolicy.matureStabilityDays -> MemoryCategory.MATURE
    else -> MemoryCategory.LEARNING
}

data class MemoryCounts(val total: Int = 0, val new: Int = 0, val mature: Int = 0) {
    val learning: Int get() = total - new - mature
}

data class InsightsPeriod(val today: LocalDate, val zone: ZoneId) {
    val currentStart: Long get() = start(today.minusDays(InsightsPolicy.observationDays - 1))
    val previousStart: Long get() = start(today.minusDays(InsightsPolicy.observationDays * 2 - 1))
    val historyStart: Long get() = start(today.minusDays(InsightsPolicy.historyDays - 1))
    val end: Long get() = start(today.plusDays(1))
    private fun start(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Minimal projection of existing logs. No predictions or inferred legacy values. */
data class RecallReviewEvent(
    val id: String,
    val cardId: String,
    val lessonId: String,
    val reviewedAt: Long,
    val rating: Int,
    val previousDueAt: Long,
    val elapsedDays: Double,
    val previousInterval: Int,
    val previousState: String,
    val reps: Int,
) {
    val eligible: Boolean get() = rating in 1..4 && previousState == "review" && reps >= 2 &&
        previousDueAt > 0 && reviewedAt >= previousDueAt && previousInterval >= 1 &&
        elapsedDays.isFinite() && elapsedDays >= 1.0
    // Migration 3→4 and older backups have no trustworthy eligibility information.
    val missingAudit: Boolean get() = previousDueAt <= 0 || reps <= 0 ||
        previousState !in setOf("new", "learning", "relearning", "review") || !elapsedDays.isFinite()
}

data class RecallSample(val successful: Int = 0, val total: Int = 0, val distinctCards: Int = 0) {
    val sufficient: Boolean get() = total >= InsightsPolicy.minimumEvents && distinctCards >= InsightsPolicy.minimumCards
    val rate: Double? get() = if (sufficient) successful.toDouble() / total else null
}

data class AttentionEvidence(val cardId: String, val lessonId: String, val failures: Int, val attempts: Int, val lastFailureAt: Long) {
    val failureRate: Double get() = failures.toDouble() / attempts
}

data class AttentionGroup(val lessonId: String, val affectedCards: Int, val example: AttentionEvidence)

data class RecallHistoryAnalysis(
    val period: InsightsPeriod,
    val current: RecallSample = RecallSample(),
    val previous: RecallSample = RecallSample(),
    val excludedCurrent: Int = 0,
    val missingAuditCurrent: Int = 0,
    val attention: List<AttentionGroup> = emptyList(),
) {
    val change: Double? get() = current.rate?.let { currentRate -> previous.rate?.let { currentRate - it } }
}

/** Current metadata is fetched only for the selected examples, never for every log. */
data class AttentionCardDetails(
    val cardId: String, val lessonId: String, val front: String, val lessonTitle: String,
    val subjectName: String, val difficulty: Double, val lapses: Int, val stability: Double,
    val scheduledDays: Int, val dueAt: Long,
)

data class AttentionLesson(val group: AttentionGroup, val card: AttentionCardDetails)

data class LearningInsights(
    val memory: MemoryCounts,
    val history: RecallHistoryAnalysis,
    val attention: List<AttentionLesson>,
    val now: Long,
    val predicted: CurrentPredictedRecall = CurrentPredictedRecall(),
)

/** Active-card projection only: the DAO excludes suspended/archived content. */
data class PredictionCardState(
    val cardId: String, val state: String?, val reps: Int?,
    val lastReviewedAt: Long?, val stability: Double?,
) {
    internal val validMemory: Boolean get() = state == "review" && (reps ?: 0) > 0 &&
        stability != null && stability.isFinite() && stability > 0 &&
        lastReviewedAt != null && lastReviewedAt > 0
}

data class CurrentPredictedRecall(
    val eligibleCards: Int = 0, val activeCards: Int = 0, val probability: Double? = null,
)

/** Read-only current estimates. Reuses the scheduler's model, including stability bounds.
 * Unchanged card data is evaluated once per UTC day, not once per UI recomposition/tick.
 * Future timestamps becoming valid and clock rollback also invalidate the cache.
 */
class CurrentRecallPredictor {
    private val model = Fsrs6MemoryModel()
    private var previousCards: List<PredictionCardState>? = null
    private var previousDay = Long.MIN_VALUE
    private var calculatedAt = Long.MIN_VALUE
    private var nextValidAt = Long.MAX_VALUE
    private var cached = CurrentPredictedRecall()

    fun calculate(cards: List<PredictionCardState>, now: Long): CurrentPredictedRecall {
        val day = Math.floorDiv(now, 86_400_000L)
        if (previousCards === cards && day == previousDay && now >= calculatedAt && now < nextValidAt) return cached
        var sum = 0.0
        var count = 0
        nextValidAt = Long.MAX_VALUE
        cards.forEach { card ->
            if (card.validMemory) {
                val reviewedAt = card.lastReviewedAt!!
                if (reviewedAt > now) nextValidAt = minOf(nextValidAt, reviewedAt)
                else {
                    val elapsedDays = (day - Math.floorDiv(reviewedAt, 86_400_000L)).toDouble()
                    sum += model.forgettingCurve(elapsedDays, card.stability!!)
                    count++
                }
            }
        }
        previousCards = cards
        previousDay = day
        calculatedAt = now
        return CurrentPredictedRecall(count, cards.size, if (count == 0) null else sum / count).also { cached = it }
    }
}

/** Event-based self-report. Initial exposure, practice, retries and unknown legacy events are excluded. */
fun analyzeRecallHistory(events: List<RecallReviewEvent>, period: InsightsPeriod, now: Long): RecallHistoryAnalysis {
    val historyStart = period.historyStart
    val currentStart = period.currentStart
    val previousStart = period.previousStart
    val end = period.end
    val eligible = mutableListOf<RecallReviewEvent>()
    val seenDays = HashSet<Pair<String, LocalDate>>()
    var excluded = 0
    var missingAudit = 0
    // Stable ordering makes the first eligible event/day independent of DAO or backup order.
    events.asSequence().filter { it.reviewedAt >= historyStart && it.reviewedAt < end && it.reviewedAt <= now }
        .sortedWith(compareBy<RecallReviewEvent> { it.reviewedAt }.thenBy { it.id }).forEach { event ->
            val inCurrent = event.reviewedAt >= currentStart
            if (event.eligible) {
                val date = Instant.ofEpochMilli(event.reviewedAt).atZone(period.zone).toLocalDate()
                if (seenDays.add(event.cardId to date)) eligible += event
                else if (inCurrent) excluded++
            } else if (inCurrent) {
                excluded++
                if (event.missingAudit) missingAudit++
            }
        }
    fun sample(from: Long, until: Long): RecallSample {
        val window = eligible.filter { it.reviewedAt >= from && it.reviewedAt < until }
        return RecallSample(window.count { it.rating != 1 }, window.size, window.map { it.cardId }.toSet().size)
    }
    val evidenceOrder = compareByDescending<AttentionEvidence> { it.failureRate }
        .thenByDescending { it.failures }.thenByDescending { it.lastFailureAt }.thenBy { it.cardId }
    val flagged = eligible.groupBy { it.cardId }.values.mapNotNull { history ->
        val recent = history.takeLast(5)
        val failures = recent.filter { it.rating == 1 }
        if (recent.size < 3 || failures.size < 2 || recent.takeLast(2).all { it.rating != 1 }) null
        else AttentionEvidence(recent.last().cardId, recent.last().lessonId, failures.size, recent.size, failures.last().reviewedAt)
    }.sortedWith(evidenceOrder)
    // Group insertion order follows the strongest evidence; raw due counts never rank suggestions.
    val suggestions = flagged.groupBy { it.lessonId }.map { (lessonId, cards) ->
        AttentionGroup(lessonId, cards.size, cards.first())
    }.take(3)
    return RecallHistoryAnalysis(period, sample(currentStart, end),
        sample(previousStart, currentStart), excluded, missingAudit, suggestions)
}
