package com.example.myapplication4

import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test

class ReviewHistoryTest {
    @Test fun previousAndForwardNeverChangeRatingsSkipCountOrCurrentQuestion() {
        val current = ReviewSessionProgress(index = 3, revealed = true, skipped = 1, counts = listOf(0, 0, 2, 0))
        val previous = current.previousCard()
        assertEquals(2, previous.historyIndex)
        assertEquals(1, previous.previousCard().historyIndex)
        assertEquals(0, previous.previousCard().previousCard().historyIndex)
        assertEquals(current.index, previous.index)
        assertEquals(current.counts, previous.counts)
        assertEquals(current.skipped, previous.skipped)
        assertTrue(previous.revealed)
        assertNull(previous.nextHistoryCard().historyIndex)
        assertEquals(current, previous.nextHistoryCard())
    }
    @Test fun beginningAndSavingCannotNavigateAndEndCanRevisitLastCard() {
        assertEquals(ReviewSessionProgress(), ReviewSessionProgress().previousCard())
        val saving = ReviewSessionProgress(index = 2, saving = true)
        assertEquals(saving, saving.previousCard())
        val complete = ReviewSessionProgress(index = 10)
        assertEquals(9, complete.previousCard().historyIndex)
        assertEquals(complete, complete.previousCard().nextHistoryCard())
    }
}
