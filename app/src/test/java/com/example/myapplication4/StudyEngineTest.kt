package com.example.myapplication4

import com.example.myapplication4.data.ReviewStateEntity
import com.example.myapplication4.data.ScheduleBlockEntity
import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import com.example.myapplication4.ui.design.isolateStudyText
import com.example.myapplication4.data.*

class StudyEngineTest {
    private val scheduler = FsrsScheduler()
    @Test fun newCardUsesOrderedCalculatedIntervals() {
        val now = 1_000_000L
        val preview = scheduler.preview(ReviewStateEntity(cardId = "c"), now)
        assertEquals(10 * 60_000L, preview.getValue(Rating.AGAIN).intervalMillis)
        assertEquals(1, preview.getValue(Rating.HARD).nextDays)
        assertEquals(2, preview.getValue(Rating.GOOD).nextDays)
        assertEquals(8, preview.getValue(Rating.EASY).nextDays)
        assertTrue(preview.getValue(Rating.HARD).intervalMillis < preview.getValue(Rating.GOOD).intervalMillis)
        assertTrue(preview.getValue(Rating.GOOD).intervalMillis < preview.getValue(Rating.EASY).intervalMillis)
    }
    @Test fun repeatedGoodReviewsGrowAdaptively() {
        var state = ReviewStateEntity(cardId = "c")
        var now = 1_000_000L
        val intervals = mutableListOf<Int>()
        repeat(5) {
            val result = scheduler.schedule(state, Rating.GOOD, now)
            intervals += result.nextDays
            state = result.state
            now = result.state.dueAt
        }
        println("FSRS Good progression at 90%: $intervals days")
        assertTrue(intervals.zipWithNext().all { (before, after) -> after > before })
        assertTrue(intervals.last() > intervals.first())
    }
    @Test fun againIsShortRelearningIntervalAndCountsLapse() {
        val result = scheduler.schedule(ReviewStateEntity(cardId = "c", reps = 2, stability = 4.0), Rating.AGAIN, 1_000_000L)
        assertEquals(10 * 60_000L, result.state.dueAt - 1_000_000L)
        assertEquals(1, result.state.lapses)
        assertEquals("relearning", result.state.state)
        assertTrue(result.state.stability < 4.0)
    }
    @Test fun previewAndCommittedScheduleAreIdentical() {
        val state = ReviewStateEntity(cardId = "c", state = "review", dueAt = 8_000L, lastReviewedAt = 1_000L, stability = 8.0, difficulty = 6.0, scheduledDays = 8, reps = 4)
        val now = 9 * 86_400_000L + 1_000L
        val preview = scheduler.preview(state, now).getValue(Rating.EASY)
        val committed = scheduler.schedule(state, Rating.EASY, now)
        assertEquals(preview, committed)
        assertEquals(now + committed.nextDays * 86_400_000L, committed.state.dueAt)
        assertEquals(state.dueAt, committed.previousDueAt)
    }
    @Test fun fsrs6PortMatchesOfficialMemoryVector() {
        val model = Fsrs6MemoryModel()
        val ratings = listOf(Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        val elapsed = listOf(0.0, 0.0, 1.0, 3.0, 8.0, 21.0)
        var memory: FsrsMemory? = null
        ratings.indices.forEach { index -> memory = memory?.let { model.next(it, ratings[index], elapsed[index]) } ?: model.initial(ratings[index]) }
        assertEquals(53.6269, memory!!.stability, .00005)
        assertEquals(6.3575, memory!!.difficulty, .00005)
    }
    @Test fun intervalFormatterUsesCompactUnits() {
        assertEquals("30s", formatReviewInterval(30_000L))
        assertEquals("10m", formatReviewInterval(10 * 60_000L))
        assertEquals("3h", formatReviewInterval(3 * 3_600_000L))
        assertEquals("3d", formatReviewInterval(3 * 86_400_000L))
        assertEquals("2w", formatReviewInterval(14 * 86_400_000L))
        assertEquals("2mo", formatReviewInterval(60 * 86_400_000L))
    }
    @Test fun importRejectsMalformedAndDeduplicatesWithinPayload() {
        assertTrue(RecallImportParser.parse("nope") is ImportResult.Failure)
        val parsed = RecallImportParser.parse("""{"version":1,"subject":"Chemistry","lesson":"Ions","cards":[{"type":"qa","front":"What?","back":"This."},{"type":"qa","front":"What?","back":"Different."}]}""") as ImportResult.Success
        assertEquals(2, parsed.draft.cards.size)
        assertTrue(parsed.draft.cards[1].duplicate)
    }
    @Test fun importAcceptsFencedJsonAndNormalizesProviderCardTypes() {
        val fence = 96.toChar().toString().repeat(3)
        val raw = fence + "json\n" + """{"version":1,"subject":"Physics","lesson":"Ohm","cards":[{"type":"basic","front":"What is V = IR?","back":"Ohm's law."},{"type":"fill_blank","front":"Voltage is {{V}}","back":"V"}]}""" + "\n" + fence
        val result = RecallImportParser.parse(raw) as ImportResult.Success
        assertEquals(listOf("qa", "cloze"), result.draft.cards.map { it.type })
    }
    @Test fun importAcceptsJsonSurroundedByProviderCommentary() {
        val raw = "Here is the requested file:\n" +
            """{"version":2,"subject":"Chemistry","lesson":"Iron","cards":[{"type":"qa","front":"Formula?","back":"[[chem:Fe₂O₃]]"}]}""" +
            "\nI hope this helps."
        val result = RecallImportParser.parse(raw) as ImportResult.Success
        assertEquals("[[chem:Fe₂O₃]]", result.draft.cards.single().back)
    }
    @Test fun truncatedImportExplainsThatClosingStructureIsMissing() {
        val result = RecallImportParser.parse("""{"version":2,"subject":"Chemistry","lesson":"Iron","cards":[{"type":"qa","front":"Why?","back":"Because."}""") as ImportResult.Failure
        assertTrue(result.message.contains("incomplete"))
        assertTrue(result.message.contains("missing"))
        assertTrue(result.message.contains(".json file directly"))
    }
    @Test fun importDeduplicatesUnicodeAndWhitespaceVariants() {
        val raw = """{"version":1,"subject":"Chemistry","lesson":"Ions","cards":[{"type":"qa","front":"  What   is Mn²⁺? ","back":"An ion."},{"type":"qa","front":"what is Mn2+?","back":"Same idea."}]}"""
        val result = RecallImportParser.parse(raw) as ImportResult.Success
        assertTrue(result.draft.cards[1].duplicate)
    }
    @Test fun backupRoundTripPreservesStudyDataAndSettings() {
        val subject = SubjectEntity(id = "s", name = "Chemistry")
        val lesson = LessonEntity(id = "l", subjectId = "s", title = "Ions")
        val card = CardEntity(id = "c", lessonId = "l", front = "Why?", back = "Because.")
        val state = ReviewStateEntity(cardId = "c", reps = 3)
        val log = ReviewLogEntity(cardId = "c", reviewedAt = 2000, rating = 3, previousInterval = 2, nextInterval = 8, previousStability = 2.3, newStability = 8.1, durationMillis = 7000, previousDueAt = 1000, nextDueAt = 3000, elapsedDays = 2.25, previousDifficulty = 6.1, newDifficulty = 5.8, previousState = "learning", newState = "review", reps = 3, lapses = 1)
        val data = BackupData(listOf(subject), emptyList(), listOf(lesson), listOf(card), emptyList(), emptyList(), listOf(state), listOf(log), emptyList(), emptyList())
        val settings = UserSettings(desiredRetention = .93, newCardLimit = 30, themeMode = "dark", language = "ar")
        val restored = RecallBackupCodec.decode(RecallBackupCodec.encode(data, settings)) as BackupResult.Success
        assertEquals("Chemistry", restored.data.subjects.single().name)
        assertEquals(3, restored.data.states.single().reps)
        assertEquals(log, restored.data.logs.single())
        assertEquals(.93, restored.settings.desiredRetention, .0001)
        assertEquals("ar", restored.settings.language)
    }
    @Test fun overnightQuietBlockDefersUntilMorning() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 9, 7, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val quiet = ScheduleBlockEntity(name="Sleep",type="quiet",startMinute=23*60,endMinute=7*60,days="1,2,3,4,5,6,7")
        val result = NotificationPolicy(zone).nextAllowed(now, listOf(quiet), null, false)
        assertTrue(result.deliverAt!! > now)
        assertEquals(7, ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.deliverAt), zone).hour)
    }
    @Test fun quietWinsOverStudyWindow() {
        val zone=ZoneId.of("UTC"); val now=ZonedDateTime.of(2026,9,7,19,30,0,0,zone).toInstant().toEpochMilli()
        val blocks=listOf(ScheduleBlockEntity(name="Study",type="window",startMinute=18*60,endMinute=21*60,days="1,2,3,4,5,6,7"),ScheduleBlockEntity(name="Rest",type="quiet",startMinute=19*60,endMinute=20*60,days="1,2,3,4,5,6,7"))
        val result=NotificationPolicy(zone).nextAllowed(now,blocks,null,true)
        assertEquals(20,ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.deliverAt!!),zone).hour)
    }
    @Test fun mixedArabicTechnicalRunsAreIsolatedWithoutReversal() {
        val rendered = isolateStudyText("توزيع Mn²⁺ هو [Ar] 3d⁵ وقانون V = IR الساعة 8:30 PM")
        assertTrue(rendered.contains("\u2066Mn²⁺\u2069"))
        assertTrue(rendered.contains("\u2066[Ar] 3d⁵\u2069"))
        assertTrue(rendered.contains("\u2066V = IR\u2069"))
        assertFalse(rendered.contains("⁵d3"))
    }
    @Test fun englishPhrasesStayTogetherInsideArabicParagraphs() {
        val raw = "لأن توزيعه [Ar] 3d⁵ — a half-filled d subshell."
        val rendered = isolateStudyText(raw)
        assertTrue(rendered.contains("\u2066[Ar] 3d⁵ — a half-filled d subshell.\u2069"))
        assertEquals(raw, rendered.replace("\u2066", "").replace("\u2069", ""))
    }
}
