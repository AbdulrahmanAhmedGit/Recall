package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.CardWithLesson
import com.example.myapplication4.ui.components.RecallDestination
import com.example.myapplication4.ui.components.RecallDock
import com.example.myapplication4.ui.design.RecallSpacing
import com.example.myapplication4.ui.design.RecallMotion
import com.example.myapplication4.ui.design.RecallSizes

private sealed interface RecallRoute {
    data object Main : RecallRoute
    data object Info : RecallRoute
    data object Calendar : RecallRoute
    data class Subject(val id: String) : RecallRoute
    data class Lesson(val id: String) : RecallRoute
    data class Import(val subjectId: String? = null, val lessonId: String? = null) : RecallRoute
    data class Review(val cards: List<CardWithLesson>) : RecallRoute
}

@Composable
fun RecallRoot(vm: RecallViewModel) { PronunciationHost(vm) { RecallContent(vm) } }

@Composable
private fun RecallContent(vm: RecallViewModel) {
    val s = recallStrings()

    var destination by rememberSaveable { mutableStateOf(RecallDestination.Today) }
    val routeSaver = remember(vm) { listSaver<RecallRoute, String>(
        save = { current -> when (current) {
            RecallRoute.Main -> listOf("main")
            RecallRoute.Info -> listOf("info")
            RecallRoute.Calendar -> listOf("calendar")
            is RecallRoute.Subject -> listOf("subject", current.id)
            is RecallRoute.Lesson -> listOf("lesson", current.id)
            is RecallRoute.Import -> listOf("import", current.subjectId.orEmpty(), current.lessonId.orEmpty())
            is RecallRoute.Review -> { vm.activeReviewCards = current.cards; listOf("review") }
        } },
        restore = { saved -> when (saved.firstOrNull()) {
            "info" -> RecallRoute.Info
            "calendar" -> RecallRoute.Calendar
            "subject" -> RecallRoute.Subject(saved[1])
            "lesson" -> RecallRoute.Lesson(saved[1])
            "import" -> RecallRoute.Import(saved[1].ifEmpty { null }, saved[2].ifEmpty { null })
            "review" -> vm.activeReviewCards?.let { RecallRoute.Review(it) } ?: RecallRoute.Main
            else -> RecallRoute.Main
        } },
    ) }
    var route by rememberSaveable(stateSaver = routeSaver) { mutableStateOf<RecallRoute>(RecallRoute.Main) }
    LaunchedEffect(route is RecallRoute.Review) { if (route !is RecallRoute.Review) vm.activeReviewCards = null }
    val mainState = rememberSaveableStateHolder()
    val reviewLink by vm.pendingReviewLink.collectAsStateWithLifecycle()
    val introductionSeen by vm.introductionSeen.collectAsStateWithLifecycle()
    LaunchedEffect(reviewLink) {
        if (reviewLink) {
            vm.pendingReviewLink.value = false
            vm.reviewDue { route = RecallRoute.Review(it) }
        }
    }
    BackHandler(route !is RecallRoute.Main) { route = RecallRoute.Main }
    val notice by vm.notice.collectAsStateWithLifecycle()
    notice?.let { androidx.compose.material3.AlertDialog(onDismissRequest = { vm.notice.value = null }, title = { androidx.compose.material3.Text(s(R.string.ui_action_error)) }, text = { androidx.compose.material3.Text(it) }, confirmButton = { androidx.compose.material3.TextButton({ vm.notice.value = null }) { androidx.compose.material3.Text(s(R.string.ui_ok)) } }) }
    // Wait for DataStore rather than briefly showing onboarding to returning users.
    if (introductionSeen == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    if (introductionSeen == false) {
        RecallInfoScreen(vm.settings.collectAsStateWithLifecycle().value.language, firstLaunch = true, onClose = { vm.completeIntroduction() })
        return
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(route, contentKey = { it.key() }, transitionSpec = { fadeIn(tween(RecallMotion.quick)) togetherWith fadeOut(tween(RecallMotion.quick)) }, label = "route") { current ->
            when (current) {
                RecallRoute.Main -> mainState.SaveableStateProvider(destination) {
                    // The dock floats visually, but must not intercept content taps
                    // or cover focus/bring-into-view targets in scrollable screens.
                    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = RecallSizes.dockHeight + RecallSpacing.md)) {
                        RecallMain(destination, vm) { route = it }
                    }
                }
                RecallRoute.Info -> RecallInfoScreen(vm.settings.collectAsStateWithLifecycle().value.language, onClose = { route = RecallRoute.Main })
                RecallRoute.Calendar -> ReviewCalendarScreen(vm, { route = RecallRoute.Main }, { route = RecallRoute.Lesson(it) })
                is RecallRoute.Subject -> SubjectScreen(current.id, vm, { route = RecallRoute.Main }, { route = RecallRoute.Lesson(it) }, { route = RecallRoute.Review(it) }, { route = RecallRoute.Import(current.id) })
                is RecallRoute.Lesson -> { val lessons by vm.lessons.collectAsStateWithLifecycle(); val lesson = lessons.firstOrNull { it.id == current.id }; LessonScreen(current.id, lesson, vm, { route = lesson?.subjectId?.let { RecallRoute.Subject(it) } ?: RecallRoute.Main }, { route = RecallRoute.Review(it) }, { route = RecallRoute.Import(lesson?.subjectId, current.id) }) }
                is RecallRoute.Import -> ImportScreen(vm, current.subjectId, current.lessonId) { route = current.subjectId?.let { RecallRoute.Subject(it) } ?: RecallRoute.Main }
                is RecallRoute.Review -> ReviewScreen(current.cards, vm) { route = RecallRoute.Main }
            }
        }
        AnimatedVisibility(route is RecallRoute.Main, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            RecallDock(destination, { destination = it }, Modifier.padding(bottom = RecallSpacing.xs))
        }
    }
}

// Never use a full review queue (including every answer) as an animation hash key.
private fun RecallRoute.key(): String = when (this) {
    RecallRoute.Main -> "main"
    RecallRoute.Info -> "info"
    RecallRoute.Calendar -> "calendar"
    is RecallRoute.Subject -> "subject:$id"
    is RecallRoute.Lesson -> "lesson:$id"
    is RecallRoute.Import -> "import:$subjectId:$lessonId"
    is RecallRoute.Review -> "review"
}

@Composable
private fun RecallMain(destination: RecallDestination, vm: RecallViewModel, navigate: (RecallRoute) -> Unit) {
    // Keep aggregate subscriptions out of the review flow. Each rating used to
    // invalidate the whole dashboard even while its screen was hidden.
    when (destination) {
        RecallDestination.Today -> {
            val due by vm.dueCount.collectAsStateWithLifecycle()
            val lessons by vm.lessons.collectAsStateWithLifecycle()
            TodayScreen(due, lessons, vm, { navigate(RecallRoute.Review(it)) }, { navigate(RecallRoute.Import()) }, { navigate(RecallRoute.Calendar) })
        }
        RecallDestination.Library -> {
            val subjects by vm.subjects.collectAsStateWithLifecycle()
            val lessons by vm.lessons.collectAsStateWithLifecycle()
            LibraryScreen(subjects, lessons, vm, { navigate(RecallRoute.Subject(it)) }, { navigate(RecallRoute.Import()) })
        }
        RecallDestination.Insights -> {
            val lessons by vm.lessons.collectAsStateWithLifecycle()
            InsightsScreen(vm, { navigate(RecallRoute.Calendar) }, { navigate(RecallRoute.Lesson(it)) })
        }
        RecallDestination.Settings -> SettingsScreen(vm) { navigate(RecallRoute.Info) }
    }
}
