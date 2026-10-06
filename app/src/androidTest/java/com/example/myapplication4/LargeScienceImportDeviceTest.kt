package com.example.myapplication4

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.domain.ImportResult
import com.example.myapplication4.domain.RecallImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeScienceImportDeviceTest {
    @Test
    fun completeIronLessonParsesOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val raw = context.assets.open("chemistry-iron-lesson-v2-science.json").bufferedReader().use { it.readText() }
        assertEquals(29_544, raw.length)
        val result = RecallImportParser.parse(raw)
        assertTrue((result as? ImportResult.Failure)?.let { "${it.message}\n${it.diagnostic}" }.orEmpty(), result is ImportResult.Success)
        assertEquals(88, (result as ImportResult.Success).draft.cards.size)
    }
}
