package com.example.myapplication4

import com.example.myapplication4.data.ScheduleBlockEntity
import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderPolicyTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val policy = NotificationPolicy(zone)
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private fun block(type: String, start: Int, end: Int, days: String = "1,2,3,4,5,6,7") =
        ScheduleBlockEntity(name = type, type = type, startMinute = start * 60, endMinute = end * 60, days = days)
    @Test fun dueDuringSchoolWaitsForEveningAndQuietWins() {
        val blocks = listOf(block("quiet", 8, 14), block("window", 19, 22), block("quiet", 19, 20))
        assertEquals(at(11, 20), policy.nextAllowed(at(11, 10), blocks, null, true).deliverAt)
    }
    @Test fun pauseEndingInsideSleepStillWaitsUntilAllowedTime() {
        val sleep = block("quiet", 23, 7)
        assertEquals(at(12, 7), policy.nextAllowed(at(11, 22), listOf(sleep), at(12, 2), false).deliverAt)
    }
    @Test fun quietAllDayDoesNotFallBackIntoQuietTime() {
        assertNull(policy.nextAllowed(at(11, 12), listOf(block("quiet", 0, 0)), null, true).deliverAt)
    }
    @Test fun farAwayWindowDoesNotPostponeForDays() {
        val window = block("window", 19, 21, "1")
        assertEquals(at(11, 12), policy.nextAllowed(at(11, 12), listOf(window), null, true).deliverAt)
    }
    @Test fun dailyDedupAndOptionalWindowStartHaveFourHourMinimum() {
        val now = at(11, 12)
        assertTrue(reminderDue(now, null, null, null, false, zone))
        assertFalse(reminderDue(now + 60_000, now, null, null, false, zone))
        assertFalse(reminderDue(at(11, 13), now, "window", null, true, zone))
        assertTrue(reminderDue(at(11, 19), now, "window", null, true, zone))
        assertFalse(reminderDue(at(11, 19), now, "window", null, false, zone))
        assertFalse(reminderDue(at(11, 19), now, "window", "window", true, zone))
        assertTrue(reminderDue(at(12, 12), now, null, null, false, zone))
    }
    @Test fun overnightWindowHasStableStartDateAndLimitedStartReminder() {
        val window = block("window", 23, 1)
        assertNotNull(policy.startingWindow(at(11, 23, 15), listOf(window)))
        assertNull(policy.startingWindow(at(12, 0, 10), listOf(window)))
    }
    @Test fun dstTransitionUsesInstantsAndNeverReturnsAnEarlierTime() {
        val berlin = ZoneId.of("Europe/Berlin")
        val before = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, berlin).toInstant().toEpochMilli()
        val result = NotificationPolicy(berlin).nextAllowed(before, listOf(block("quiet", 23, 7)), null, false)
        assertTrue(result.deliverAt!! > before)
        assertEquals(7, Instant.ofEpochMilli(result.deliverAt).atZone(berlin).hour)
    }
}
