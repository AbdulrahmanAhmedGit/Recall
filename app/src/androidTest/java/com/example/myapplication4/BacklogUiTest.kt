package com.example.myapplication4

import android.app.Application
import androidx.room.Room
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.TodayScreen
import com.example.myapplication4.ui.ReviewScreen
import com.example.myapplication4.ui.design.RecallTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BacklogUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun optionalBatchPreservesFullCountSkipAndRevealedStateAcrossRecreation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(app, RecallDatabase::class.java).build()
        val vm = RecallViewModel(app, db)
        val store = androidx.lifecycle.ViewModelStore().apply { put("phase3-ui", vm) }
        try {
            db.dao().insertSubject(SubjectEntity(id = "s", name = "Chemistry"))
            db.dao().insertLesson(LessonEntity(id = "l", subjectId = "s", title = "العناصر الانتقالية"))
            repeat(250) { i ->
                val id = "c$i"
                db.dao().insertCard(CardEntity(id = id, lessonId = "l", front = "لماذا يكون Mn²⁺ مستقرًا؟ $i", back = "[[chem:[Ar] 3d⁵]]"))
                db.dao().saveState(ReviewStateEntity(id, dueAt = 0, reps = 2, state = "review", stability = 2.0))
            }
            var cards by mutableStateOf<List<CardWithLesson>?>(null)
            val restoration = StateRestorationTester(compose)
            restoration.setContent { RecallTheme { Surface {
                if (cards == null) TodayScreen(250, emptyList(), vm, { cards = it }, {})
                else ReviewScreen(cards!!, vm) { cards = null }
            } } }
            compose.onNodeWithText("250").assertIsDisplayed()
            compose.onNodeWithText("Review all due cards").assertIsDisplayed()
            compose.onNodeWithText("Review up to 20 cards").performClick()
            compose.waitUntil(5000) { cards?.size == 20 }
            compose.waitUntil(5000) { vm.reviewGesturesSeen.value != null }
            if (vm.reviewGesturesSeen.value == false) compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Skip for now").performClick()
            compose.runOnIdle { vm.skipReviewCard(cards!!.first().id) }
            assertEquals(1, vm.reviewProgress.value.skipped) // A stale second tap cannot skip the unseen next question.
            assertTrue(db.dao().allLogs().isEmpty())
            compose.onNodeWithText("Show answer").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Good").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { vm.reviewProgress.value.index == 2 }
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Show answer").assertIsDisplayed()
            assertEquals(1, db.dao().allLogs().size)
            assertEquals(249, db.dao().dueCount(System.currentTimeMillis()).first())
            assertEquals(1, vm.reviewProgress.value.skipped)
            compose.onNodeWithText("Previous card").performClick()
            assertEquals(1, vm.reviewProgress.value.historyIndex)
            compose.onNodeWithText("Show answer").performClick()
            compose.onNodeWithText("Good").assertDoesNotExist()
            restoration.emulateSavedInstanceStateRestore()
            assertEquals(1, vm.reviewProgress.value.historyIndex)
            compose.onNodeWithText("Previous card").performClick()
            assertEquals(0, vm.reviewProgress.value.historyIndex) // The skipped question is still available.
            compose.runOnIdle {
                val skippedCard = cards!!.first()
                vm.submitReview(skippedCard, vm.preview(skippedCard, System.currentTimeMillis()), com.example.myapplication4.domain.Rating.GOOD) {}
            }
            assertEquals(1, db.dao().allLogs().size) // Read-only history cannot create another review.
            compose.onNodeWithText("Next card").performClick()
            compose.onNodeWithText("Next card").performClick()
            assertNull(vm.reviewProgress.value.historyIndex)
            assertEquals(2, vm.reviewProgress.value.index)
            assertEquals(1, vm.reviewProgress.value.skipped)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
