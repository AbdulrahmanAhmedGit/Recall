package com.example.myapplication4

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.SettingsScreen
import com.example.myapplication4.ui.design.RecallTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReminderSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sixSchedulerTapsUnlockPersistentNotificationTestAndFullExportIsReachable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val original = app.recallPreferences.data.first()
        UserPreferences(app).setDebugMode(false)
        val vm = RecallViewModel(app, Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build())
        val store = androidx.lifecycle.ViewModelStore().apply { put("settings", vm) }
        compose.setContent { RecallTheme { Surface { SettingsScreen(vm) {} } } }
        try {
            compose.onNodeWithText("Export all data").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Scheduler information").performScrollTo().performClick()
            repeat(4) { compose.onAllNodesWithText("Scheduler information").onLast().performClick() }
            assertFalse(app.recallPreferences.data.first()[PreferenceKeys.debugMode] ?: false)
            compose.onAllNodesWithText("Scheduler information").onLast().performClick()
            compose.waitUntil(5000) { runBlocking { app.recallPreferences.data.first()[PreferenceKeys.debugMode] == true } }
            compose.onNodeWithText("Test notification").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5000) { app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id == 1002 } }
            compose.onNodeWithText("Disable debug mode").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { app.recallPreferences.data.first()[PreferenceKeys.debugMode] == false } }
        } finally {
            app.getSystemService(android.app.NotificationManager::class.java).cancel(1002)
            app.recallPreferences.updateData { original }
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
