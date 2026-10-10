package com.example.myapplication4

import com.example.myapplication4.domain.*
import com.example.myapplication4.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReviewFocusTest {
    @Test fun parentPausesCoverNewLessonsAndOverlapsRemainIndependent() {
        val subject = ReviewPause(ReviewPauseScope.SUBJECT, "s", 100, 200)
        val chapter = ReviewPause(ReviewPauseScope.CHAPTER, "ch", 100, 300)
        val lesson = ReviewPause(ReviewPauseScope.LESSON, "other", 100, 400)
        val scopes = listOf(PauseLessonScope("direct", "s", null), PauseLessonScope("nested", "s", "ch"),
            PauseLessonScope("other", "t", null), PauseLessonScope("unpaused", "t", "other-ch"))
        val pauses = listOf(subject, chapter, lesson)
        assertEquals(listOf("direct", "nested", "other"), pauses.excludedLessonIds(150, scopes))
        assertEquals(listOf("nested", "other"), pauses.excludedLessonIds(200, scopes))
        assertEquals(listOf("nested", "other"), listOf(chapter, lesson).excludedLessonIds(150, scopes))
        assertTrue("new" in pauses.excludedLessonIds(150, scopes + PauseLessonScope("new", "s", null)))
        assertTrue(pauses.excludedLessonIds(99, scopes).isEmpty())
        assertTrue(pauses.excludedLessonIds(400, scopes).isEmpty())
        assertEquals(pauses, ReviewPauseCodec.decode(ReviewPauseCodec.encode(pauses)))
    }

    @Test fun backupsPreserveAllScopesAndRejectMissingParentReferences() {
        val subject = SubjectEntity(name = "Chemistry")
        val chapter = ChapterEntity(subjectId = subject.id, name = "Iron")
        val lesson = LessonEntity(subjectId = subject.id, chapterId = chapter.id, title = "Reduction")
        val data = BackupData(listOf(subject), listOf(chapter), listOf(lesson), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val settings = UserSettings(reviewPauses = listOf(ReviewPause(ReviewPauseScope.SUBJECT, subject.id, 1, 100),
            ReviewPause(ReviewPauseScope.CHAPTER, chapter.id, 1, 100), ReviewPause(ReviewPauseScope.LESSON, lesson.id, 1, 100)))
        val encoded = RecallBackupCodec.encode(data, settings)
        assertEquals(settings.reviewPauses.toSet(), (RecallBackupCodec.decode(encoded) as BackupResult.Success).settings.reviewPauses.toSet())
        val malformed = JSONObject(encoded).apply { getJSONObject("settings").getJSONArray("container_pauses").getJSONObject(0).put("target_id", "missing") }
        assertTrue(RecallBackupCodec.decode(malformed.toString()) is BackupResult.Failure)
        val legacy = JSONObject(encoded).apply { getJSONObject("settings").remove("container_pauses") }
        assertEquals(1, (RecallBackupCodec.decode(legacy.toString()) as BackupResult.Success).settings.reviewPauses.size)
    }
    @Test fun backupPreservesPausesAndAcceptsLegacySettings() {
        val subject = SubjectEntity(name = "Chemistry")
        val lesson = LessonEntity(subjectId = subject.id, title = "Iron")
        val data = BackupData(listOf(subject), emptyList(), listOf(lesson), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val settings = UserSettings(reviewPauses = listOf(ReviewPause(ReviewPauseScope.LESSON, lesson.id, 1, 100)))
        val encoded = RecallBackupCodec.encode(data, settings)
        assertEquals(settings.lessonPauses, (RecallBackupCodec.decode(encoded) as BackupResult.Success).settings.lessonPauses)
        val legacy = JSONObject(encoded).apply { getJSONObject("settings").remove("lesson_pauses") }
        assertTrue((RecallBackupCodec.decode(legacy.toString()) as BackupResult.Success).settings.lessonPauses.isEmpty())
        val malformed = JSONObject(encoded).apply { getJSONObject("settings").put("lesson_pauses", "invalid") }
        assertTrue(RecallBackupCodec.decode(malformed.toString()) is BackupResult.Failure)
        val missingLesson = JSONObject(encoded).apply { getJSONObject("settings").getJSONArray("lesson_pauses").getJSONObject(0).put("lesson_id", "missing") }
        assertTrue(RecallBackupCodec.decode(missingLesson.toString()) is BackupResult.Failure)
    }
    @Test fun pauseBoundariesAreInclusiveStartExclusiveEnd() {
        val pause = LessonReviewPause("lesson", 100, 200)
        assertFalse(pause.activeAt(99)); assertTrue(pause.activeAt(100))
        assertTrue(pause.activeAt(199)); assertFalse(pause.activeAt(200))
        assertEquals(listOf("lesson"), listOf(pause).pausedLessonIds(150))
        assertTrue(listOf(pause).pausedLessonIds(200).isEmpty())
    }
    @Test fun localDaysHandleMidnightAndDaylightSaving() {
        val zone = ZoneId.of("Europe/Berlin")
        val spring = lessonPauseDays("a", LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29), zone)
        assertEquals(23 * 3_600_000L, spring.endAt - spring.startAt)
        val autumn = lessonPauseDays("a", LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), zone)
        assertEquals(25 * 3_600_000L, autumn.endAt - autumn.startAt)
        assertFalse(spring.activeAt(spring.endAt))
    }
    @Test fun preferencesRoundTripWithoutLosingFuturePauses() {
        val values = listOf(LessonReviewPause("one", 100, 200), LessonReviewPause("two", 300, 500))
        assertEquals(values, LessonPauseCodec.decode(LessonPauseCodec.encode(values)))
        assertEquals(listOf("two"), values.pausedLessonIds(400))
        assertTrue(LessonPauseCodec.readPreference("broken").isEmpty())
        assertTrue(LessonPauseCodec.readPreference(null).isEmpty())
    }
    @Test fun malformedAndDuplicatePeriodsAreRejected() {
        for (raw in listOf("""[{"lesson_id":"x","start_at":20,"end_at":10}]""",
            """[{"lesson_id":"x","start_at":1,"end_at":2},{"lesson_id":"x","start_at":3,"end_at":4}]""")) {
            assertTrue(runCatching { LessonPauseCodec.decode(raw) }.isFailure)
        }
        assertTrue(runCatching { lessonPauseDays("x", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 2), ZoneId.of("UTC")) }.isFailure)
    }
}
