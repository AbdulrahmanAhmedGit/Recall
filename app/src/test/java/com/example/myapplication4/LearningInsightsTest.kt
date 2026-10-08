package com.example.myapplication4

import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LearningInsightsTest {
    @Test fun frozenPresentationTimeBelongsToPreviousLocalDayEvenWhenSubmittedAfterMidnight() {
        val zone = ZoneId.of("Africa/Cairo")
        val submittedDay = LocalDate.of(2026, 10, 8)
        val presented = submittedDay.minusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val submitted = submittedDay.atTime(0, 1).atZone(zone).toInstant().toEpochMilli()
        val p = InsightsPeriod(submittedDay, zone)
        val event = RecallReviewEvent("frozen", "c", "l", presented, 3, presented - 1000, 2.0, 2, "review", 4)
        // At the end of a 30-day window this presentation-time event falls out a day
        // earlier than a hypothetical submission-time event. No backfilled tap time.
        val later = InsightsPeriod(submittedDay.plusDays(29), zone)
        assertEquals(1, analyzeRecallHistory(listOf(event), p, submitted).current.total)
        assertEquals(0, analyzeRecallHistory(listOf(event), later, later.end - 1).current.total)
        assertEquals(1, analyzeRecallHistory(listOf(event.copy(reviewedAt = submitted)), later, later.end - 1).current.total)
    }
    private val period = InsightsPeriod(LocalDate.of(2026, 10, 8), ZoneId.of("Africa/Cairo"))
    private val now = period.today.atTime(23, 59).atZone(period.zone).toInstant().toEpochMilli()
    private fun event(card: String = "c", daysAgo: Long = 0, rating: Int = 3, lesson: String = "l", id: String = "$card-$daysAgo") =
        RecallReviewEvent(id, card, lesson, period.today.minusDays(daysAgo).atTime(12, 0).atZone(period.zone).toInstant().toEpochMilli(),
            rating, period.today.minusDays(daysAgo).atStartOfDay(period.zone).toInstant().toEpochMilli(), 2.0, 2, "review", 4)

    @Test fun maturityUsesMemoryStateAndFiniteStabilityRatherThanAnAnswerCount() {
        assertEquals(MemoryCategory.NEW, memoryCategory(0, "review", 100.0))
        assertEquals(MemoryCategory.NEW, memoryCategory(-1, "new", 0.4))
        assertEquals(MemoryCategory.LEARNING, memoryCategory(1, "learning", 21.0))
        assertEquals(MemoryCategory.LEARNING, memoryCategory(9, "relearning", 100.0))
        assertEquals(MemoryCategory.LEARNING, memoryCategory(9, "review", 20.999))
        assertEquals(MemoryCategory.MATURE, memoryCategory(9, "review", 21.0))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            assertEquals(MemoryCategory.LEARNING, memoryCategory(9, "review", it))
        }
        val categories = listOf(memoryCategory(0, "new", .4), memoryCategory(1, "learning", .4), memoryCategory(8, "review", 35.0))
        assertEquals(MemoryCategory.entries.toSet(), categories.toSet())
        val counts = MemoryCounts(3, 1, 1)
        assertEquals(counts.total, counts.new + counts.learning + counts.mature)
    }

    @Test fun samplesAreEventsNotLibraryPercentagesAndBothSampleGatesApply() {
        val events = (0..9).flatMap { card -> (0L..2L).map { day -> event("c$card", day, if (card == 0) 1 else 2 + card % 3) } }
        val result = analyzeRecallHistory(events, period, now)
        assertEquals(RecallSample(27, 30, 10), result.current)
        assertEquals(.9, result.current.rate!!, 0.0)
        assertNull(result.change)
        assertNull(RecallSample(29, 29, 10).rate)
        assertNull(RecallSample(30, 30, 9).rate)
        assertEquals(RecallSample(), analyzeRecallHistory(emptyList(), period, now).current)
    }

    @Test fun firstLearningRelearningPracticeRetriesInvalidAndLegacyLogsAreExcluded() {
        val good = event()
        val invalid = listOf(
            good.copy(id = "new", previousState = "new", reps = 1),
            good.copy(id = "learning", previousState = "learning"),
            good.copy(id = "relearning", previousState = "relearning"),
            good.copy(id = "practice", previousDueAt = good.reviewedAt + 1),
            good.copy(id = "retry", elapsedDays = .999),
            good.copy(id = "zero-interval", previousInterval = 0),
            good.copy(id = "bad-rating", rating = 5),
            good.copy(id = "legacy", previousDueAt = 0, reps = 0, previousState = "new", elapsedDays = 0.0),
            good.copy(id = "bad-time", elapsedDays = Double.NaN),
        )
        val result = analyzeRecallHistory(invalid + good, period, now)
        assertEquals(RecallSample(1, 1, 1), result.current)
        assertEquals(9, result.excludedCurrent)
        assertEquals(2, result.missingAuditCurrent)
    }

    @Test fun firstEligibleEventPerLocalDayWinsWithoutCountingRetrySuccessAsRecall() {
        val failed = event(rating = 1)
        val retry = failed.copy(id = "retry", reviewedAt = failed.reviewedAt + 600_000, rating = 3, previousState = "relearning", elapsedDays = .01)
        val duplicate = failed.copy(id = "later", reviewedAt = failed.reviewedAt + 1000, rating = 3)
        val result = analyzeRecallHistory(listOf(duplicate, retry, event(daysAgo = 1), failed), period, now)
        assertEquals(RecallSample(1, 2, 1), result.current)
        assertEquals(2, result.excludedCurrent)
        // Resolve imported equal-time events consistently by ID, not list order.
        assertEquals(0, analyzeRecallHistory(listOf(failed.copy(id = "z", rating = 3), failed.copy(id = "a")), period, now).current.successful)
    }

    @Test fun localPeriodsCrossYearsAndIgnoreFutureAndOutOfRangeRecords() {
        val january = InsightsPeriod(LocalDate.of(2027, 1, 2), ZoneId.of("Asia/Kolkata"))
        fun at(date: LocalDate, id: String) = event(id = id).copy(reviewedAt = date.atTime(0, 15).atZone(january.zone).toInstant().toEpochMilli(), previousDueAt = 1)
        val result = analyzeRecallHistory(listOf(
            at(january.today.minusDays(29), "start-current"), at(january.today.minusDays(30), "end-previous"),
            at(january.today.minusDays(59), "start-previous"), at(january.today.minusDays(60), "older"),
            at(january.today.minusDays(90), "too-old"), at(january.today.plusDays(1), "future"),
        ), january, january.today.atTime(12, 0).atZone(january.zone).toInstant().toEpochMilli())
        assertEquals(1, result.current.total)
        assertEquals(2, result.previous.total)
        assertEquals(LocalDate.of(2026, 12, 4), java.time.Instant.ofEpochMilli(january.currentStart).atZone(january.zone).toLocalDate())
        val futureToday = event().copy(reviewedAt = now + 1)
        assertEquals(0, analyzeRecallHistory(listOf(futureToday), period, now).current.total)
    }

    @Test fun localMidnightAndDstUseLocalDatesButElapsedEligibilityUsesFull24Hours() {
        val autumn = InsightsPeriod(LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York"))
        fun at(hour: Int, minute: Int, id: String) = event(id = id).copy(
            reviewedAt = autumn.today.atTime(hour, minute).atZone(autumn.zone).toInstant().toEpochMilli(), previousDueAt = 1)
        // The long DST day can contain two >24h responses; still count only the first local-day event.
        val first = at(0, 15, "first").copy(rating = 1)
        val last = at(23, 45, "last")
        assertTrue(last.reviewedAt - first.reviewedAt > 86_400_000L)
        assertEquals(RecallSample(0, 1, 1), analyzeRecallHistory(listOf(last, first), autumn, autumn.end - 1).current)
        val spring = InsightsPeriod(LocalDate.of(2026, 3, 8), autumn.zone)
        assertEquals(23 * 3_600_000L, spring.end - spring.today.atStartOfDay(spring.zone).toInstant().toEpochMilli())
        assertFalse(event().copy(elapsedDays = 23.0 / 24).eligible)
        val india = InsightsPeriod(period.today, ZoneId.of("Asia/Kolkata"))
        val before = event(id = "before").copy(reviewedAt = india.today.atStartOfDay(india.zone).toInstant().toEpochMilli() - 1, previousDueAt = 1)
        val after = before.copy(id = "after", reviewedAt = before.reviewedAt + 2)
        assertEquals(2, analyzeRecallHistory(listOf(before, after), india, india.end - 1).current.total)
    }

    @Test fun comparisonIsDescriptiveAndRequiresBothPeriodsToMeetBothGates() {
        val current = (0..9).flatMap { c -> (0L..2L).map { event("c$c", it, if (c == 0) 1 else 3) } }
        val previous = (0..9).flatMap { c -> (30L..32L).map { event("c$c", it, if (c < 2) 1 else 3) } }
        assertEquals(.1, analyzeRecallHistory(current + previous, period, now).change!!, .00001)
        assertNull(analyzeRecallHistory(current + previous.drop(1), period, now).change)
    }

    @Test fun attentionRequiresRepeatedDelayedFailuresAndTwoRecentSuccessesClearIt() {
        val struggling = listOf(event(daysAgo = 4, rating = 1), event(daysAgo = 3), event(daysAgo = 2, rating = 1))
        val result = analyzeRecallHistory(struggling, period, now)
        assertEquals(AttentionEvidence("c", "l", 2, 3, event(daysAgo = 2).reviewedAt), result.attention.single().example)
        assertTrue(analyzeRecallHistory(struggling.take(2), period, now).attention.isEmpty())
        assertTrue(analyzeRecallHistory(struggling + event(daysAgo = 1) + event(), period, now).attention.isEmpty())
        assertTrue(analyzeRecallHistory(struggling.map { it.copy(elapsedDays = .01) }, period, now).attention.isEmpty())
        assertTrue(analyzeRecallHistory(listOf(event(daysAgo = 91, rating = 1)) + struggling.drop(1), period, now).attention.isEmpty())
    }

    @Test fun attentionUsesLastFiveAndShowsAtMostThreeLessonsWithoutAnUrgencyScore() {
        val events = (0..4).flatMap { lesson -> (0L..4L).map { day ->
            event("c$lesson", day, if (day < 2 || lesson == 0) 1 else 3, "l$lesson")
        } } + listOf(event("same-lesson", 0, 1, "l0"), event("same-lesson", 1, 1, "l0"), event("same-lesson", 2, 3, "l0"))
        val result = analyzeRecallHistory(events, period, now)
        assertEquals(3, result.attention.size)
        assertEquals("l0", result.attention.first().lessonId)
        assertEquals(2, result.attention.first().affectedCards)
        val recoveredOldFailures = (0L..8L).map { event(daysAgo = it, rating = if (it > 4) 1 else 3) }
        assertTrue(analyzeRecallHistory(recoveredOldFailures, period, now).attention.isEmpty())
    }
}
