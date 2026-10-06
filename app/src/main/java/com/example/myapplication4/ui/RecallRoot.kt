package com.example.myapplication4.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.CardWithLesson
import com.example.myapplication4.ui.components.RecallDestination
import com.example.myapplication4.ui.components.RecallDock
import com.example.myapplication4.ui.design.RecallSpacing

private sealed interface RecallRoute {
    data object Main : RecallRoute
    data object Info : RecallRoute
    data class Subject(val id: String) : RecallRoute
    data class Lesson(val id: String) : RecallRoute
    data class Import(val subjectId: String? = null, val lessonId: String? = null) : RecallRoute
    data class Review(val cards: List<CardWithLesson>) : RecallRoute
}

@Composable
fun RecallRoot(vm: RecallViewModel) { PronunciationHost(vm) { RecallContent(vm) } }

@Composable
private fun RecallContent(vm: RecallViewModel) {
    var destination by rememberSaveable { mutableStateOf(RecallDestination.Today) }
    var route by remember { mutableStateOf<RecallRoute>(RecallRoute.Main) }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    val due by vm.dueCount.collectAsStateWithLifecycle()
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
    notice?.let { androidx.compose.material3.AlertDialog(onDismissRequest = { vm.notice.value = null }, title = { androidx.compose.material3.Text("Could not complete action") }, text = { androidx.compose.material3.Text(it) }, confirmButton = { androidx.compose.material3.TextButton({ vm.notice.value = null }) { androidx.compose.material3.Text("OK") } }) }
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
        AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { current ->
            when (current) {
                RecallRoute.Main -> when (destination) {
                    RecallDestination.Today -> TodayScreen(due, lessons, vm, { route = RecallRoute.Review(it) }, { route = RecallRoute.Import() })
                    RecallDestination.Library -> LibraryScreen(subjects, lessons, vm, { route = RecallRoute.Subject(it) }, { route = RecallRoute.Import() })
                    RecallDestination.Insights -> InsightsScreen(vm, lessons)
                    RecallDestination.Settings -> SettingsScreen(vm) { route = RecallRoute.Info }
                }
                RecallRoute.Info -> RecallInfoScreen(vm.settings.collectAsStateWithLifecycle().value.language, onClose = { route = RecallRoute.Main })
                is RecallRoute.Subject -> SubjectScreen(current.id, vm, { route = RecallRoute.Main }, { route = RecallRoute.Lesson(it) }, { route = RecallRoute.Review(it) }, { route = RecallRoute.Import(current.id) })
                is RecallRoute.Lesson -> { val lesson = lessons.firstOrNull { it.id == current.id }; LessonScreen(current.id, lesson, vm, { route = lesson?.subjectId?.let { RecallRoute.Subject(it) } ?: RecallRoute.Main }, { route = RecallRoute.Review(it) }, { route = RecallRoute.Import(lesson?.subjectId, current.id) }) }
                is RecallRoute.Import -> ImportScreen(vm, current.subjectId, current.lessonId) { route = current.subjectId?.let { RecallRoute.Subject(it) } ?: RecallRoute.Main }
                is RecallRoute.Review -> ReviewScreen(current.cards, vm) { route = RecallRoute.Main }
            }
        }
        AnimatedVisibility(route is RecallRoute.Main, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            RecallDock(destination, { destination = it }, Modifier.padding(bottom = RecallSpacing.xs))
        }
    }
}
