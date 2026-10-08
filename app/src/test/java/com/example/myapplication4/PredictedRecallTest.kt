package com.example.myapplication4

import com.example.myapplication4.data.ReviewStateEntity
import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class PredictedRecallTest {
    private val day = 86_400_000L
    private val now = Instant.parse("2026-10-08T12:00:00Z").toEpochMilli()
    private fun card(id: String = "c", stability: Double? = 10.0, at: Long? = now - 10 * day) =
        PredictionCardState(id, "review", 4, at, stability)

    @Test fun equalWeightMeanAndCoverageUseActualEligibleCards() {
        val model = Fsrs6MemoryModel()
        val cards = listOf(card("a", 10.0), card("b", 30.0), card("new").copy(reps = 0))
        val result = CurrentRecallPredictor().calculate(cards, now)
        assertEquals(2, result.eligibleCards)
        assertEquals(3, result.activeCards)
        assertEquals((model.forgettingCurve(10.0, 10.0) + model.forgettingCurve(10.0, 30.0)) / 2, result.probability!!, 1e-14)
        assertEquals(.9, CurrentRecallPredictor().calculate(listOf(card()), now).probability!!, 1e-14)
    }

    @Test fun parityWithExistingModelIncludesSchedulerStabilityBounds() {
        val model = Fsrs6MemoryModel()
        for (stability in listOf(.00001, .1, .212, 8.2956, 21.0, 36500.0, 1e100)) {
            for (elapsed in listOf(0L, 1L, 7L, 30L, 365L)) {
                val estimate = CurrentRecallPredictor().calculate(listOf(card(stability = stability, at = now - elapsed * day)), now)
                assertEquals(model.forgettingCurve(elapsed.toDouble(), stability), estimate.probability!!, 0.0)
                assertTrue(estimate.probability in 0.0..1.0)
            }
        }
    }

    @Test fun utcMidnightRatherThanFractionalOrLocalDaysDeterminesElapsedTime() {
        val before = Instant.parse("2026-10-08T23:59:00Z").toEpochMilli()
        val after = before + 120_000
        val cards = listOf(card(at = before))
        val predictor = CurrentRecallPredictor()
        assertEquals(1.0, predictor.calculate(cards, before + 30_000).probability!!, 0.0)
        assertEquals(Fsrs6MemoryModel().forgettingCurve(1.0, 10.0), predictor.calculate(cards, after).probability!!, 0.0)
        // Cairo midnight occurs within this UTC day: the prediction must not change.
        val localBoundary = Instant.parse("2026-10-08T21:00:00Z").toEpochMilli()
        val localCards = listOf(card(at = localBoundary - 60_000))
        assertEquals(1.0, predictor.calculate(localCards, localBoundary + 60_000).probability!!, 0.0)
    }

    @Test fun changingDeviceTimezoneDoesNotChangeTheUtcEstimate() {
        val previous = TimeZone.getDefault()
        try {
            val results = listOf("Africa/Cairo", "Pacific/Kiritimati", "America/Los_Angeles").map {
                TimeZone.setDefault(TimeZone.getTimeZone(it))
                CurrentRecallPredictor().calculate(listOf(card()), now)
            }
            assertEquals(1, results.toSet().size)
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun invalidMemoryAndUnknownLegacyTimestampsAreExcludedNotTreatedAsZeroRecall() {
        val cards = listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).map { card(stability = it) } +
            listOf(card(at = null), card(at = 0), card(at = -1), card(at = now + 1),
                card().copy(state = "new"), card().copy(state = "learning"), card().copy(state = "relearning"),
                card().copy(state = "unknown"), card().copy(state = null), card().copy(reps = null), card().copy(reps = 0), card().copy(reps = -1))
        val result = CurrentRecallPredictor().calculate(cards, now)
        assertEquals(cards.size, result.activeCards)
        assertEquals(0, result.eligibleCards)
        assertNull(result.probability)
        assertEquals(CurrentPredictedRecall(), CurrentRecallPredictor().calculate(emptyList(), now))
    }

    @Test fun minuteTicksReuseEstimatesButFutureValidityClockRollbackAndChangedStatesRefresh() {
        val predictor = CurrentRecallPredictor()
        val cards = listOf(card(), card("future", at = now + 120_000))
        val initial = predictor.calculate(cards, now)
        assertSame(initial, predictor.calculate(cards, now + 60_000))
        val admitted = predictor.calculate(cards, now + 120_000)
        assertEquals(2, admitted.eligibleCards)
        assertNotSame(initial, admitted)
        assertEquals(1, predictor.calculate(cards, now).eligibleCards)
        assertEquals(0, predictor.calculate(cards.map { it.copy(state = "relearning") }, now).eligibleCards)
    }

    @Test fun analyticsDoesNotChangeAnySchedulerOutputOrStoredState() {
        val state = ReviewStateEntity(cardId = "c", state = "review", lastReviewedAt = now - 10 * day,
            stability = 10.0, reps = 5, difficulty = 6.0, scheduledDays = 10, dueAt = now)
        val scheduler = FsrsScheduler()
        val original = scheduler.preview(state, now, .93)
        CurrentRecallPredictor().calculate(listOf(card()), now)
        assertEquals(original, scheduler.preview(state, now, .93))
        val memory = Fsrs6MemoryModel()
        original.forEach { (rating, result) ->
            val expected = memory.next(FsrsMemory(state.stability, state.difficulty), rating, 10.0)
            assertEquals(expected.stability, result.state.stability, 0.0)
            assertEquals(expected.difficulty, result.state.difficulty, 0.0)
        }
    }
}
