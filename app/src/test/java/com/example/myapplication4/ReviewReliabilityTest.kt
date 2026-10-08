package com.example.myapplication4

import com.example.myapplication4.domain.*
import com.example.myapplication4.data.ReviewStateEntity
import org.junit.Assert.*
import org.junit.Test

class ReviewReliabilityTest {
    @Test fun timerExcludesBackgroundAndKeepsDurationAcrossRecreationAndClockChanges() {
        val timer = ReviewTimer()
        timer.begin("a", 1000); timer.pause(2000)
        assertEquals(1000L, timer.duration(1_000_000))
        timer.begin("a", 1_000_000)
        assertEquals(1500L, timer.duration(1_000_500))
        timer.pause(1_000_500); timer.resume(1_001_000)
        assertEquals(2000L, timer.duration(1_001_500))
        timer.begin("b", 1_002_000)
        assertEquals(0L, timer.duration(1_002_000))
    }
    @Test fun completionTimeAnchorsHistoryAndDueDatesWithoutChangingFsrs() {
        val scheduler = FsrsScheduler()
        val old = ReviewStateEntity(cardId = "a", dueAt = 0)
        val shown = scheduler.preview(old, 86_280_000L)
        val fresh = scheduler.preview(old, 87_300_000L)
        assertTrue(sameReviewIntervals(shown, fresh))
        for (rating in Rating.entries) {
            val result = fresh.getValue(rating)
            assertEquals(87_300_000L, result.reviewedAt)
            assertEquals(result.reviewedAt + result.intervalMillis, result.state.dueAt)
            assertEquals(scheduler.schedule(old, rating, 87_300_000L), result)
        }
    }
    @Test fun delayedPreviewChangesRequireConfirmationAndBacklogHasNoDailyQuota() {
        val scheduler = FsrsScheduler()
        val state = ReviewStateEntity(cardId = "a", state = "review", dueAt = 0, lastReviewedAt = 1, stability = 3.0, reps = 5)
        assertFalse(sameReviewIntervals(scheduler.preview(state, 86_400_000), scheduler.preview(state, 60L * 86_400_000)))
        listOf(0, 1, 59).forEach { assertFalse(BacklogPolicy.isBacklog(it)) }
        listOf(60, 61, 250, 10_000).forEach { assertTrue(BacklogPolicy.isBacklog(it)) }
        assertEquals(20, BacklogPolicy.batchSize)
    }
}
