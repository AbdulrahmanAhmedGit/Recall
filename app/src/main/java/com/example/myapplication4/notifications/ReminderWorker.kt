package com.example.myapplication4.notifications

import android.content.Context
import androidx.work.*
import androidx.datastore.preferences.core.*
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

object ReminderDiagnostics {
    val lastRun = longPreferencesKey("reminder_last_run")
    val lastSent = longPreferencesKey("reminder_last_sent")
    val lastWindow = stringPreferencesKey("reminder_last_window")
    val reason = stringPreferencesKey("reminder_reason")
    val nextEligible = longPreferencesKey("reminder_next_eligible")
}

/** Persistent periodic safety net plus a coalesced immediate check after relevant edits. */
class ReminderWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val openDatabase: () -> RecallDatabase = { RecallDatabase.create(context) },
    private val clock: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = lock.withLock {
        val db = openDatabase()
        try {
            val prefs = applicationContext.recallPreferences.data.first()
            val now = clock()
            val notificationContext = com.example.myapplication4.util.RecallLocale.context(applicationContext, prefs[PreferenceKeys.language] ?: "system")
            suspend fun record(reason: String, next: Long? = null) {
                applicationContext.recallPreferences.edit {
                    it[ReminderDiagnostics.lastRun] = now
                    it[ReminderDiagnostics.reason] = reason
                    if (next == null) it.remove(ReminderDiagnostics.nextEligible) else it[ReminderDiagnostics.nextEligible] = next
                }
            }
            if (prefs[PreferenceKeys.remindersEnabled] != true) {
                record("Reminders are disabled"); return@withLock Result.success()
            }
            val dao = db.dao()
            val due = dao.reminderDueCount(now, UserPreferences(applicationContext).settings.first().reviewPauses.excludedLessonIds(now, dao.pauseLessonScopes().first()))
            if (due == 0) {
                record("No due cards; background checks remain active")
                applicationContext.getSystemService(android.app.NotificationManager::class.java).cancel(1001)
                return@withLock Result.success()
            }
            ReminderNotifications.blockedReason(notificationContext)?.let {
                record(it); return@withLock Result.success()
            }
            val blocks = dao.schedules().first()
            val policy = NotificationPolicy()
            val decision = policy.nextAllowed(now, blocks, prefs[PreferenceKeys.pausedUntil], true)
            if (decision.deliverAt == null || decision.deliverAt > now) {
                record(decision.reason, decision.deliverAt); return@withLock Result.success()
            }
            val window = policy.startingWindow(now, blocks)
            val lastSent = prefs[ReminderDiagnostics.lastSent]
            // Recover safely after a backwards device-clock adjustment.
            val effectiveLast = lastSent?.takeIf { it <= now }
            if (!reminderDue(now, effectiveLast, window, prefs[ReminderDiagnostics.lastWindow], prefs[PreferenceKeys.studyWindowReminder] != false)) {
                record("Already reminded; waiting for the next day or study window")
                return@withLock Result.success()
            }
            // Recheck preferences immediately before posting in case the user paused meanwhile.
            val latest = applicationContext.recallPreferences.data.first()
            if (latest[PreferenceKeys.remindersEnabled] != true || (latest[PreferenceKeys.pausedUntil] ?: 0) > now) {
                record("Reminders disabled or paused"); return@withLock Result.success()
            }
            val latestDue = dao.reminderDueCount(now, UserPreferences(applicationContext).settings.first().reviewPauses.excludedLessonIds(now, dao.pauseLessonScopes().first()))
            if (latestDue == 0) {
                applicationContext.getSystemService(android.app.NotificationManager::class.java).cancel(1001)
                record("No unpaused due cards"); return@withLock Result.success()
            }
            val error = ReminderNotifications.post(com.example.myapplication4.util.RecallLocale.context(applicationContext, latest[PreferenceKeys.language] ?: "system"), latestDue, windowStart = window != null && prefs[PreferenceKeys.studyWindowReminder] != false)
            if (error != null) record(error) else applicationContext.recallPreferences.edit {
                it[ReminderDiagnostics.lastRun] = now
                it[ReminderDiagnostics.lastSent] = now
                if (window != null) it[ReminderDiagnostics.lastWindow] = window
                it[ReminderDiagnostics.reason] = "Posted reminder for " + latestDue + " due cards"
                it.remove(ReminderDiagnostics.nextEligible)
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            applicationContext.recallPreferences.edit { it[ReminderDiagnostics.reason] = "Background check failed; retry scheduled" }
            Result.retry()
        } finally { db.close() }
    }

    companion object {
        private val lock = Mutex()
        const val PERIODIC = "recall-reminder-periodic-v2"
        const val CHECK = "recall-reminder-check-v2"
        fun ensure(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork("recall-reminder")
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
        fun schedule(context: Context, delayMillis: Long = 0L) {
            ensure(context)
            WorkManager.getInstance(context).enqueueUniqueWork(CHECK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply { cancelUniqueWork(PERIODIC); cancelUniqueWork(CHECK); cancelUniqueWork("recall-reminder") }
            context.getSystemService(android.app.NotificationManager::class.java).cancel(1001)
        }
    }
}
