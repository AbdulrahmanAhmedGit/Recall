package com.example.myapplication4

import android.app.Application
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.LessonTagChips
import com.example.myapplication4.ui.design.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Preview51RegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dynamicSurfacesFollowAndroidPaletteAndLongTagsWrapAtLargeFontScale() {
        var dark by mutableStateOf(false)
        var edits = 0
        val tags = listOf(TagEntity("a", "acid-radicals"), TagEntity("b", "precipitation"), TagEntity("c", "qualitative-analysis"))
        compose.setContent {
            val context = LocalContext.current
            RecallTheme(darkTheme = dark, dynamicColor = true, language = "ar") {
                if (Build.VERSION.SDK_INT >= 31) {
                    val expected = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    assertEquals(expected.primary, MaterialTheme.colorScheme.primary)
                    assertEquals(expected.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceInteractive)
                    assertEquals(expected.surfaceContainerLow, MaterialTheme.colorScheme.surfaceElevated)
                }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Column(Modifier.width(320.dp).padding(16.dp)) {
                        BidiAwareText("التحليل الكيميائي والكشف عن الشقوق الحمضية", style = MaterialTheme.typography.titleLarge)
                        LessonTagChips(tags, { edits++ }, {})
                    }
                }
            }
        }
        tags.forEach { tag ->
            val node = compose.onNodeWithTag("lesson-tag-${tag.id}").assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top <= 80.dp)
        }
        compose.onNodeWithTag("lesson-tag-c").performClick()
        compose.runOnIdle { assertEquals(1, edits); dark = true }
        tags.forEach { tag ->
            val node = compose.onNodeWithTag("lesson-tag-${tag.id}").assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top <= 80.dp)
        }
    }

    @Test fun parentPausesMigrateLegacyPreferencesAndCoverLaterAddedLessons(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val original = app.recallPreferences.data.first()
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        try {
            val dao = db.dao()
            val now = System.currentTimeMillis()
            dao.insertSubject(SubjectEntity("s", "Chemistry"))
            dao.insertChapter(ChapterEntity("ch", "s", "Iron"))
            dao.insertLesson(LessonEntity("direct", "s", title = "Direct"))
            dao.insertLesson(LessonEntity("nested", "s", "ch", "Nested"))
            listOf("direct", "nested").forEach { id ->
                dao.insertCard(CardEntity(id = "card-$id", lessonId = id, front = "Question", back = "Answer"))
                dao.saveState(ReviewStateEntity(cardId = "card-$id", dueAt = 0))
            }
            val before = dao.allStates()
            val legacy = LessonReviewPause("direct", now - 1, now + 86_400_000)
            app.recallPreferences.edit { it.remove(PreferenceKeys.reviewPauses); it[PreferenceKeys.lessonPauses] = LessonPauseCodec.encode(listOf(legacy)); it[PreferenceKeys.remindersEnabled] = false }
            val prefs = UserPreferences(app)
            assertEquals(listOf(legacy.asReviewPause()), prefs.settings.first().reviewPauses)
            val subject = ReviewPause(ReviewPauseScope.SUBJECT, "s", now - 1, now + 86_400_000)
            prefs.setReviewPause(subject.scope, subject.targetId, subject)
            assertNull(app.recallPreferences.data.first()[PreferenceKeys.lessonPauses])
            dao.insertLesson(LessonEntity("new", "s", title = "Created after pause"))
            val paused = prefs.settings.first().reviewPauses.excludedLessonIds(now, dao.pauseLessonScopes().first())
            assertEquals(setOf("direct", "nested", "new"), paused.toSet())
            assertEquals(0, dao.reminderDueCount(now, paused))
            assertTrue(dao.dueCards(now, excludedLessonIds = paused).isEmpty())
            prefs.setReviewPause(subject.scope, subject.targetId, null)
            val remaining = prefs.settings.first().reviewPauses.excludedLessonIds(now, dao.pauseLessonScopes().first())
            assertEquals(listOf("direct"), remaining)
            assertEquals(1, dao.reminderDueCount(now, remaining))
            assertEquals(before, dao.allStates()); assertTrue(dao.allLogs().isEmpty())
        } finally { db.close(); app.recallPreferences.updateData { original } }
    }
}
