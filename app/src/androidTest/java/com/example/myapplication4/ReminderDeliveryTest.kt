package com.example.myapplication4

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.myapplication4.data.*
import com.example.myapplication4.notifications.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.util.UUID

class ReminderDeliveryTest {
    @Test fun noDueDoesNotEndChecksAndLaterDuePostsOnlyOnceWithDeepLinkAndPause() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = context.recallPreferences.data.first()
        val name = "reminder-test-${UUID.randomUUID()}"
        val notifications = context.getSystemService(NotificationManager::class.java)
        var now = ZonedDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        fun database() = Room.databaseBuilder(context, RecallDatabase::class.java, name).build()
        suspend fun runWorker(): ListenableWorker.Result {
            val factory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, parameters: WorkerParameters): ListenableWorker =
                    ReminderWorker(appContext, parameters, ::database) { now }
            }
            return TestListenableWorkerBuilder<ReminderWorker>(context).setWorkerFactory(factory).build().doWork()
        }
        try {
            context.recallPreferences.edit { it.clear(); it[PreferenceKeys.remindersEnabled] = true }
            assertNull("Grant notification permission before running this test", ReminderNotifications.blockedReason(context))
            ReminderWorker.ensure(context)
            assertEquals(ListenableWorker.Result.success(), runWorker())
            assertTrue(context.recallPreferences.data.first()[ReminderDiagnostics.reason]!!.startsWith("No due cards"))
            val jobs = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderWorker.PERIODIC).get()
            assertTrue(jobs.any { !it.state.isFinished })
            database().let { db ->
                try {
                    val subject = SubjectEntity(name = "Chemistry")
                    val lesson = LessonEntity(subjectId = subject.id, title = "Oxidation")
                    val card = CardEntity(lessonId = lesson.id, front = "Mn²⁺?", back = "[Ar] 3d⁵")
                    db.dao().insertSubject(subject); db.dao().insertLesson(lesson); db.dao().insertCard(card)
                    db.dao().saveState(ReviewStateEntity(cardId = card.id, dueAt = now - 1))
                } finally { db.close() }
            }
            assertEquals(ListenableWorker.Result.success(), runWorker())
            assertEquals(now, context.recallPreferences.data.first()[ReminderDiagnostics.lastSent])
            val posted = notifications.activeNotifications.single { it.id == 1001 }.notification
            assertNotNull(posted.contentIntent)
            assertEquals(2, posted.actions.size)
            now += 60_000
            runWorker()
            assertTrue(context.recallPreferences.data.first()[ReminderDiagnostics.reason]!!.startsWith("Already reminded"))
            posted.actions.last().actionIntent.send()
            val deadline = System.currentTimeMillis() + 5000
            while (context.recallPreferences.data.first()[PreferenceKeys.pausedUntil] == null && System.currentTimeMillis() < deadline) kotlinx.coroutines.delay(50)
            assertTrue(context.recallPreferences.data.first()[PreferenceKeys.pausedUntil]!! > System.currentTimeMillis())
            // A manual debug test ignores the pause but never changes the normal cooldown.
            val lastSent = context.recallPreferences.data.first()[ReminderDiagnostics.lastSent]
            assertNull(ReminderNotifications.post(context, 0, test = true))
            assertTrue(notifications.activeNotifications.any { it.id == 1002 })
            assertEquals(lastSent, context.recallPreferences.data.first()[ReminderDiagnostics.lastSent])
            database().let { db -> try { assertTrue(db.dao().allLogs().isEmpty()) } finally { db.close() } }
        } finally {
            ReminderWorker.cancel(context)
            notifications.cancel(1002)
            context.recallPreferences.updateData { original }
            context.deleteDatabase(name)
        }
    }
}
