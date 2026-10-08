package com.example.myapplication4

import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private val subject = SubjectEntity(id = "s", name = "Science")
    private val lesson = LessonEntity(id = "l", subjectId = "s", title = "Lesson")
    private val card = CardEntity(id = "c", lessonId = "l", front = "Why?", back = "Because")
    private val data = BackupData(listOf(subject), emptyList(), listOf(lesson), listOf(card), emptyList(), emptyList(), listOf(ReviewStateEntity("c")), emptyList(), emptyList(), emptyList())
    @Test fun validDataAndLegacyHistoryRemainCompatible() {
        val log = ReviewLogEntity(cardId = "c", reviewedAt = 1, rating = 3, previousInterval = 0, nextInterval = 1, previousStability = 0.0, newStability = 1.0, durationMillis = 0)
        val original = data.copy(logs = listOf(log))
        assertEquals(original, (RecallBackupCodec.decode(RecallBackupCodec.encode(original, UserSettings())) as BackupResult.Success).data)
    }
    @Test fun invalidGraphsDuplicatesAndNonFiniteMemoryAreRejectedBeforeMutation() {
        val invalid = listOf(data.copy(cards = listOf(card.copy(lessonId = "missing"))), data.copy(cards = listOf(card, card)),
            data.copy(states = listOf(ReviewStateEntity("c", stability = Double.NaN))),
            data.copy(lessonTags = listOf(LessonTagEntity("l", "missing"))),
            data.copy(resources = listOf(SubjectResourceEntity(subjectId = "missing", title = "PDF"))))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { validateBackupData(it) } }
    }
    @Test fun optionalMissingPauseTimestampIsAccepted() {
        val raw = RecallBackupCodec.encode(data, UserSettings()).replace("\"paused_until\":null,", "")
        assertTrue(RecallBackupCodec.decode(raw) is BackupResult.Success)
    }
    @Test fun streamingCodecAcceptsOldIndentedAndNewDocumentsAndRejectsTruncation() {
        val log = ReviewLogEntity(cardId = "c", reviewedAt = 1, rating = 3, previousInterval = 0, nextInterval = 1, previousStability = .4, newStability = 1.0, durationMillis = 0)
        val original = data.copy(logs = List(1000) { log.copy(id = "$it") })
        val output = java.io.StringWriter()
        RecallBackupCodec.write(output, original, UserSettings())
        for (json in listOf(output.toString(), RecallBackupCodec.encode(original, UserSettings()))) {
            assertEquals(original, (RecallBackupCodec.read(json.reader()) as BackupResult.Success).data)
            assertTrue(RecallBackupCodec.read(json.dropLast(3).reader()) is BackupResult.Failure)
            assertTrue(RecallBackupCodec.read((json + "garbage").reader()) is BackupResult.Failure)
        }
    }
}
