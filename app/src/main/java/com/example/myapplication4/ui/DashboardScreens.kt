package com.example.myapplication4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration

@Composable internal fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth < 360.dp) RecallSizes.compactPagePadding else RecallSizes.pagePadding
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = padding).widthIn(max = RecallSizes.contentMaxWidth).align(Alignment.TopCenter), content = content)
    }
}

@Composable
fun TodayScreen(due: Int, lessons: List<LessonOverview>, vm: RecallViewModel, review: (List<CardWithLesson>) -> Unit, import: () -> Unit, calendar: () -> Unit = {}) {
    val reviewLoading by vm.reviewLoading.collectAsStateWithLifecycle()
    val dueLessons = remember(lessons) { lessons.filter { it.due > 0 } }
    val upcoming = remember(lessons) { lessons.filter { it.due == 0 && it.total > 0 }.take(3) }
    ScreenFrame {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
            item {
                Spacer(Modifier.height(RecallSpacing.lg))
                Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f).padding(top = RecallSpacing.xxs).semantics { heading() })
                    ReviewCalendarAction(vm, calendar)
                }
                Spacer(Modifier.height(RecallSpacing.xl))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$due", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    Text(" cards due", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 5.dp, start = RecallSpacing.xs))
                }
                Text("${dueLessons.size} lessons · about ${if (due == 0) 0 else maxOf(1, due / 4)} minutes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted)
                RecallPrimaryButton(if (reviewLoading) "Loading…" else if (due > 0) "Start review" else "Review anyway", Icons.Outlined.AutoStories, { vm.reviewDue(review) }, Modifier.fillMaxWidth().padding(top = RecallSpacing.lg), enabled = !reviewLoading)
                SectionHeader("Due lessons", if (dueLessons.isEmpty()) "Import" else null, import)
            }
            if (dueLessons.isEmpty()) item { RecallEmptyState(Icons.Outlined.AutoStories, "You’re caught up", "New cards will appear here when they are ready.") }
            else items(dueLessons, key = { it.id }) { lesson -> LessonRow(lesson) { vm.reviewLesson(lesson.id, review) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            if (upcoming.isNotEmpty()) { item { SectionHeader("Upcoming") }; items(upcoming, key = { "up-${it.id}" }) { LessonRow(it) { vm.reviewLesson(it.id, review) } } }
        }
    }
}

@Composable
fun LibraryScreen(subjects: List<SubjectEntity>, lessons: List<LessonOverview>, vm: RecallViewModel, openSubject: (String) -> Unit, import: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    // Avoid a full lesson scan for each subject row in large libraries.
    val lessonsBySubject = remember(lessons) { lessons.groupBy { it.subjectId } }
    ScreenFrame {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item { RecallTopBar("Library", "Subjects hold chapters and lessons.", action = { RecallIconButton(Icons.Outlined.FileDownload, "Import cards", import) }); SectionHeader("Subjects", "Add") { adding = true } }
            if (subjects.isEmpty()) item { RecallEmptyState(Icons.Outlined.LocalLibrary, "Create your first subject", "You can add lessons directly—chapters stay optional.", "Add subject") { adding = true } }
            else items(subjects, key = { it.id }) { subject -> val subjectLessons = lessonsBySubject[subject.id].orEmpty(); SubjectRow(subject, subjectLessons.size, subjectLessons.sumOf { it.total }, subjectLessons.sumOf { it.due }) { openSubject(subject.id) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }
        RecallPrimaryButton("Quick add", Icons.Outlined.Add, { adding = true }, Modifier.align(Alignment.End).padding(bottom = 92.dp))
    }
    if (adding) SubjectSheet(save = { vm.addSubject(it.first, it.second); adding = false }) { adding = false }
}

@Composable
fun InsightsScreen(vm: RecallViewModel, lessons: List<LessonOverview>, calendar: () -> Unit = {}, openLesson: (String) -> Unit = {}) {
    val reviewed by vm.todayReviews.collectAsStateWithLifecycle()
    val activity by vm.studyActivity.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = if (settings.language in setOf("ar", "en")) {
        Locale.Builder().setLanguage(settings.language).apply {
            if (systemLocale.country.isNotEmpty()) setRegion(systemLocale.country)
        }.build()
    } else systemLocale
    val total = lessons.sumOf { it.total }; val learned = lessons.sumOf { it.learned }; val difficult = lessons.sumOf { it.difficult }
    ScreenFrame { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 108.dp)) {
        item { RecallTopBar("Insights", "A useful view of memory—not a scoreboard.", action = { ReviewCalendarAction(vm, calendar) }); SectionHeader("Today"); StatStrip(listOf("Reviewed" to "$reviewed", "Study time" to "~${reviewed / 4}m", "Target" to "90%")) }
        item(key = "activity") { StudyActivityCard(activity, Modifier.padding(top = RecallSpacing.lg), locale); SectionHeader("Memory") }
        item { MemoryBar(learned, total); Spacer(Modifier.height(RecallSpacing.ml)); StatStrip(listOf("Learning" to "${(total - learned).coerceAtLeast(0)}", "Mature" to "$learned", "Difficult" to "$difficult")); SectionHeader("Lessons requiring attention") }
        val attention = lessons.sortedByDescending { it.difficult + it.due }.take(4)
        if (attention.isEmpty()) item { RecallEmptyState(Icons.Outlined.BarChart, "No history yet", "Review a few cards to begin seeing memory trends.") }
        else items(attention, key = { it.id }) { LessonRow(it) { openLesson(it.id) } }
    } }
}

@Composable private fun StatStrip(values: List<Pair<String, String>>) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) { values.forEach { (label, value) -> Column(Modifier.weight(1f)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) } } } }

@Composable private fun MemoryBar(learned: Int, total: Int) { Column { Row { Text("Long-term progress", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("$learned / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.muted) }; Spacer(Modifier.height(RecallSpacing.sm)); LinearProgressIndicator(progress = { if (total == 0) 0f else learned.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant) } }
