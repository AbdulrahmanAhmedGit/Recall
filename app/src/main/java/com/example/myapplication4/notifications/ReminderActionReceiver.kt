package com.example.myapplication4.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication4.data.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val preferences = UserPreferences(context.applicationContext)
                if (intent.action == PAUSE) {
                    preferences.pauseUntil(System.currentTimeMillis() + 2 * 60 * 60_000L)
                    context.getSystemService(android.app.NotificationManager::class.java).cancel(1001)
                }
                if (preferences.settings.first().remindersEnabled) ReminderWorker.schedule(context)
            } catch (_: Exception) {
                ReminderWorker.ensure(context)
            } finally { pending.finish() }
        }
    }
    companion object { const val PAUSE = "com.example.myapplication4.PAUSE_REMINDERS" }
}
