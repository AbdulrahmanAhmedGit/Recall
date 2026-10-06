package com.example.myapplication4

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.RecallInfoScreen
import com.example.myapplication4.ui.RecallRoot
import com.example.myapplication4.ui.design.RecallTheme
import com.example.myapplication4.ui.design.isolateStudyText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecallInfoTest {
    @get:Rule val compose = createComposeRule()

    @Test fun introductionIsPersistedAndGuideReopensFromSettings(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val original = app.recallPreferences.data.first()
        app.recallPreferences.edit { it[PreferenceKeys.introductionSeen] = false; it[PreferenceKeys.language] = "en" }
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = androidx.lifecycle.ViewModelStore().apply { put("info", vm) }
        var visible by mutableStateOf(true)
        try {
            compose.setContent { RecallTheme { Surface { if (visible) RecallRoot(vm) } } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Start studying").fetchSemanticsNodes().isNotEmpty() }
            capture("guide-light-english")
            compose.onNodeWithText("Start studying").performClick()
            compose.waitUntil(5000) { vm.introductionSeen.value == true }
            assertTrue(UserPreferences(app).introductionSeen.first())
            // A backup restore does not reset this device-local onboarding preference.
            UserPreferences(app).restore(vm.settings.value)
            assertTrue(UserPreferences(app).introductionSeen.first())
            compose.runOnIdle { visible = false }
            compose.runOnIdle { visible = true }
            compose.onNodeWithText("Start studying").assertDoesNotExist()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("How Recall works").assertIsDisplayed().performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            capture("guide-settings")
            compose.onNodeWithText(isolateStudyText("What’s new · October 2026")).assertIsDisplayed()
            compose.onNodeWithText("Done").assertIsDisplayed().performClick()
            compose.onNodeWithText("How Recall works").assertIsDisplayed()
        } finally {
            compose.runOnIdle { visible = false }
            instrumentation.runOnMainSync { store.clear() }
            app.recallPreferences.updateData { original }
        }
    }

    @Test fun arabicGuideScrollsAndKeepsPrimaryActionVisible() {
        var closed = false
        compose.setContent { RecallTheme(darkTheme = true) { Surface { RecallInfoScreen("ar", firstLaunch = true) { closed = true } } } }
        capture("guide-dark-arabic")
        compose.onNodeWithText(isolateStudyText("مرحبًا بك في Recall")).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(isolateStudyText("كيف تُحدد مواعيد المراجعة؟")))
        compose.onNodeWithText(isolateStudyText("كيف تُحدد مواعيد المراجعة؟")).assertIsDisplayed()
        capture("guide-arabic-scheduler")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(isolateStudyText("المراجع العلمية والخوارزمية")))
        compose.onNodeWithText(isolateStudyText("المراجع العلمية والخوارزمية")).assertIsDisplayed()
        capture("guide-arabic-sources")
        compose.onNodeWithText("ابدأ الدراسة").assertIsDisplayed().performClick()
        assertTrue(closed)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage()
        val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "guide-validation").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
