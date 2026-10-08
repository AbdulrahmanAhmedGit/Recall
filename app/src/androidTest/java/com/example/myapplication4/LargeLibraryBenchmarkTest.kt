package com.example.myapplication4

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.io.File

/** Controlled synthetic data only. Measurements are diagnostics, not fragile speed assertions. */
class LargeLibraryBenchmarkTest {
    @Test fun compareUnboundedQueueWithBoundedSessionsAndMeasureExistingReadPaths() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val report = StringBuilder("cards,logs,all_due_ms,batch_ms,lessons_ms,subject_ms,memory_ms,history_ms,heatmap_ms,calendar_ms,preview_ms,backup_ms,zip_write_ms,zip_read_ms,zip_bytes,heap_mb\n")
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (size in listOf(100, 1000, 5000, 10000)) {
            val name = "phase3-benchmark-$size.db"
            val db = Room.databaseBuilder(app, RecallDatabase::class.java, name).build()
            try {
                val cards = (0 until size).map { CardEntity(id = "c%05d".format(it), lessonId = "l${it / 100}", front = "لماذا يكون Mn²⁺ أكثر استقرارًا؟ $it", back = "[[chem:[Ar] 3d⁵]]") }
                val states = cards.mapIndexed { i, c -> ReviewStateEntity(c.id, state = "review", dueAt = now - (i + 1) * 60_000L, lastReviewedAt = now - 10 * 86_400_000L, stability = 25.0, scheduledDays = 5, reps = 10) }
                val logs = cards.flatMap { card -> (0 until 10).map { i -> ReviewLogEntity(id = "${card.id}-$i", cardId = card.id, reviewedAt = now - (i + 1) * 86_400_000L, rating = 3, previousInterval = 1, nextInterval = 5, previousStability = 20.0, newStability = 25.0, durationMillis = 5000, previousDueAt = now - (i + 2) * 86_400_000L, elapsedDays = 1.0, previousState = "review", newState = "review", reps = i + 2) } }
                val data = BackupData(listOf(SubjectEntity(id = "s", name = "Science")), emptyList(), (0 until (size + 99) / 100).map { LessonEntity(id = "l$it", subjectId = "s", title = "Lesson $it") }, cards, emptyList(), emptyList(), states, logs, emptyList(), emptyList())
                db.dao().mergeBackup(data)
                suspend fun median(block: suspend () -> Unit): Double {
                    block() // Same warm-up for both queue implementations.
                    return (0 until 5).map { val start = System.nanoTime(); block(); (System.nanoTime() - start) / 1_000_000.0 }.sorted()[2]
                }
                val dao = db.dao()
                val all = median { assertEquals(size, dao.dueCards(now).size) }
                val batch = median { assertEquals(20, dao.dueBatch(now, 20, 20).size) }
                val lessons = median { dao.lessonOverviews(now).first() }
                val subject = median { dao.lessonOverviewsForSubject("s", now).first() }
                val memory = median { assertEquals(size, dao.insightsMemoryCounts(21.0, Double.MAX_VALUE).first().total) }
                val history = median { analyzeRecallHistory(dao.insightsReviews(InsightsPeriod(today, zone).historyStart, now + 1).first(), InsightsPeriod(today, zone), now) }
                val heatmap = median { assertEquals(size * 10, dao.studyActivity(studyActivityQuery(ActivityPeriod(today, zone), now)).first().sumOf { it.reviewCount }) }
                val calendar = median { dao.reviewCalendarCounts(reviewCalendarQuery(ReviewCalendarPeriod(YearMonth.from(today), today, zone))).first() }
                val previews = median { states.take(20).forEach { FsrsScheduler().preview(it, now) } }
                var backup = data
                val backupTime = median { backup = dao.backup() }
                val archive = File(app.cacheDir, "benchmark-$size.zip")
                val directory = File(app.cacheDir, "benchmark-$size-restored").apply { mkdirs() }
                val start = System.nanoTime()
                FullBackupArchive.write(archive.outputStream(), backup, UserSettings()) { error("No attachments") }
                val writeTime = (System.nanoTime() - start) / 1_000_000.0
                val readStart = System.nanoTime()
                val decoded = FullBackupArchive.read(archive.inputStream(), directory)
                val readTime = (System.nanoTime() - readStart) / 1_000_000.0
                assertEquals(size * 10, decoded.data.logs.size)
                val heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576
                report.append("$size,${logs.size},$all,$batch,$lessons,$subject,$memory,$history,$heatmap,$calendar,$previews,$backupTime,$writeTime,$readTime,${archive.length()},$heap\n")
                android.util.Log.i("RecallBenchmark", report.lines().last { it.isNotBlank() })
                File(app.getExternalFilesDir(null), "phase3-benchmark.csv").writeText(report.toString())
                archive.delete(); directory.delete()
            } finally { db.close(); app.deleteDatabase(name) }
        }
        File(app.getExternalFilesDir(null), "phase3-benchmark.csv").writeText(report.toString())
    }
}
