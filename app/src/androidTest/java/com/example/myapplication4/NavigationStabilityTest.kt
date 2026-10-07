package com.example.myapplication4

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.RecallRoot
import com.example.myapplication4.ui.design.RecallTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class NavigationStabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedTabChangesAndReviewStateRestorationKeepWorking(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = ViewModelStore().apply { put("navigation", vm) }
        try {
            vm.completeIntroduction().join()
            val subject = SubjectEntity(name = "Chemistry")
            val lesson = LessonEntity(subjectId = subject.id, title = "Iron")
            val card = CardEntity(lessonId = lesson.id, front = "Why is iron useful?", back = "Its alloys combine strength and useful properties.")
            db.dao().insertSubject(subject); db.dao().insertLesson(lesson)
            db.dao().insertCard(card); db.dao().saveState(ReviewStateEntity(cardId = card.id))
            val restoration = StateRestorationTester(compose)
            restoration.setContent { RecallTheme { Surface { RecallRoot(vm) } } }
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Library").fetchSemanticsNodes().isNotEmpty() }
            repeat(6) {
                for (destination in listOf("Library", "Insights", "Settings", "Today")) {
                    compose.onNodeWithContentDescription(destination).performClick()
                }
            }
            compose.onNodeWithText("Start review").performClick()
            compose.onNodeWithText("Show answer").assertIsDisplayed().performClick()
            compose.onNodeWithText("Good").assertIsDisplayed()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Good").assertIsDisplayed().performClick()
            compose.onNodeWithText("1 card reviewed").assertIsDisplayed()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithContentDescription("Library").performClick()
            compose.onNodeWithText("Chemistry").assertIsDisplayed()
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
