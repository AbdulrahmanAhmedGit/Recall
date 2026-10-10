package com.example.myapplication4

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.myapplication4.ui.design.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

class MotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun animatedListInsertRemoveAndReorderPreserveKeyedItemState() {
        var ids by mutableStateOf(listOf(1, 2, 3))
        compose.setContent { RecallTheme {
            LazyColumn {
                items(ids, key = { it }) { id ->
                    var count by remember { mutableIntStateOf(0) }
                    TextButton({ count++ }, recallItemMotion().testTag("item-$id")) { Text("$id:$count") }
                }
            }
        } }
        compose.onNodeWithTag("item-2").performClick()
        compose.runOnIdle { ids = listOf(3, 2, 4) }
        compose.onNodeWithText("2:1").assertIsDisplayed()
        compose.onNodeWithText("4:0").assertIsDisplayed()
        compose.onNodeWithTag("item-1").assertDoesNotExist()
    }

    @Test fun androidRemoveAnimationsIsObservedLiveWithoutRestart() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val key = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        val original = android.provider.Settings.Global.getString(resolver, key)
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        var enabled = true
        try {
            shell("settings put global animator_duration_scale 1")
            compose.setContent { RecallTheme { enabled = LocalRecallMotionEnabled.current; Text("Motion policy") } }
            compose.runOnIdle { assertTrue(enabled) }
            shell("settings put global animator_duration_scale 0")
            compose.waitUntil(5000) { !enabled }
            shell("settings put global animator_duration_scale 1")
            compose.waitUntil(5000) { enabled }
        } finally {
            shell(if (original == null) "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $original")
        }
    }

    @Test fun rapidNumbersKeepExactLocalizedAccessibleTargetAndProgressSettles() {
        compose.mainClock.autoAdvance = false
        var value by mutableDoubleStateOf(0.0)
        var progress by mutableFloatStateOf(0f)
        var displayedProgress = 0f
        val format = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
        compose.setContent { RecallTheme {
            val animated = animatedRecallProgress(progress)
            displayedProgress = animated.value
            AnimatedRecallNumber(value, { format.format(it) }, Modifier.testTag("number"))
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { value = 1250.5; progress = 1f }
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithTag("number").assertTextEquals("1,250.5")
        assertTrue(displayedProgress > 0f && displayedProgress < 1f)
        compose.runOnIdle { value = 42.25; progress = .5f }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("number").assertTextEquals("42.25")
        assertEquals(.5f, displayedProgress, .0001f)
    }

    @Test fun reducedMotionUpdatesInstantlyAndExpansionRetainsRtlContent() {
        compose.mainClock.autoAdvance = false
        var value by mutableDoubleStateOf(.85)
        var expanded by mutableStateOf(false)
        var progress by mutableFloatStateOf(0f)
        var displayedProgress = 0f
        val percent = NumberFormat.getPercentInstance(Locale.forLanguageTag("ar"))
        compose.setContent { RecallTheme(darkTheme = true, language = "ar") {
            CompositionLocalProvider(LocalRecallMotionEnabled provides false, LocalLayoutDirection provides LayoutDirection.Rtl) {
                val animated = animatedRecallProgress(progress)
                displayedProgress = animated.value
                Column {
                    AnimatedRecallNumber(value, { percent.format(it) }, Modifier.testTag("number"))
                    RecallExpansion(expanded) { BidiAwareText("راجع درس Electric Current اليوم", Modifier.testTag("details")) }
                }
            }
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { value = .95; progress = 1f; expanded = true }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("number").assertTextEquals(percent.format(.95))
        compose.onNodeWithTag("details").assertIsDisplayed()
        assertEquals(1f, displayedProgress, 0f)
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("details").assertDoesNotExist()
    }
}
