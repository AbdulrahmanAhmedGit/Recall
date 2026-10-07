package com.example.myapplication4.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.R
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.CardWithLesson
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DecimalStyle
import java.util.Locale

private object CalendarSizes {
    val dayMinimumWidth = 48.dp
    val dayMinimumHeight = 64.dp
    const val pageSize = 100
}

private data class CalendarLoad<T>(val value: T? = null, val failed: Boolean = false)

@Composable private fun calendarResources(locale: Locale): android.content.res.Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration, locale) {
        context.createConfigurationContext(Configuration(configuration).apply { setLocale(locale) }).resources
    }
}

@Composable internal fun ReviewCalendarAction(vm: RecallViewModel, onClick: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val system = LocalConfiguration.current.locales[0]
    val locale = if (settings.language in setOf("ar", "en")) Locale.forLanguageTag(settings.language) else system
    RecallIconButton(Icons.Outlined.CalendarMonth, calendarResources(locale).getString(R.string.calendar_title), onClick)
}

@Composable
fun ReviewCalendarScreen(vm: RecallViewModel, back: () -> Unit, openLesson: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = remember(settings.language, systemLocale) {
        if (settings.language in setOf("en", "ar")) Locale.Builder().setLanguage(settings.language).apply {
            if (systemLocale.country.isNotEmpty()) setRegion(systemLocale.country)
        }.build() else systemLocale
    }
    val resources = calendarResources(locale)
    val clock by produceState(ActivityPeriod.current()) {
        while (true) { value = ActivityPeriod.current(); delay(60_000) }
    }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(clock.today).toString()) }
    var dateKey by rememberSaveable { mutableStateOf(clock.today.toString()) }
    var limit by rememberSaveable(dateKey) { mutableIntStateOf(CalendarSizes.pageSize) }
    var selectedCardId by rememberSaveable(dateKey) { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthKey)
    val date = LocalDate.parse(dateKey)
    LaunchedEffect(clock) {
        if (date < clock.today) {
            monthKey = YearMonth.from(clock.today).toString()
            dateKey = clock.today.toString()
        }
    }
    val countsFlow = remember(vm, month, clock) {
        vm.calendarDays(month).map { CalendarLoad(value = it) }
            .catch { emit(CalendarLoad(failed = true)) }
    }
    val countsLoad = key(month, clock) {
        val load by countsFlow.collectAsStateWithLifecycle(CalendarLoad())
        load
    }
    val cardsFlow = remember(vm, date, limit, clock) {
        vm.calendarCards(date, limit).map { CalendarLoad(value = it) }
            .catch { emit(CalendarLoad(failed = true)) }
    }
    val cardsLoad = key(date, limit, clock) {
        val load by cardsFlow.collectAsStateWithLifecycle(CalendarLoad())
        load
    }
    val counts = remember(countsLoad.value) { countsLoad.value.orEmpty().associateBy { it.date } }
    val cards = cardsLoad.value.orEmpty()
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)) }
    val selectedDay = counts[date]
    val selectedTotal = selectedDay?.total ?: 0
    val selectedCard = cards.firstOrNull { it.id == selectedCardId }

    ScreenFrame {
        LazyColumn(Modifier.fillMaxSize().testTag("calendar-content"), contentPadding = PaddingValues(bottom = RecallSpacing.lg)) {
            item {
                RecallTopBar(resources.getString(R.string.calendar_title), resources.getString(R.string.calendar_subtitle), back)
                ReviewCalendarMonth(month, clock.today, counts, date, locale,
                    onMonth = { next -> monthKey = next.toString(); dateKey = maxOf(next.atDay(1), clock.today).toString() },
                    onDay = { dateKey = it.toString() })
                Text(resources.getString(R.string.calendar_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(vertical = RecallSpacing.md))
                Text(fullDate.format(date), style = MaterialTheme.typography.titleLarge)
                if (countsLoad.value != null) {
                    Text(resources.getQuantityString(R.plurals.calendar_count, selectedTotal, numbers.format(selectedTotal)),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (selectedTotal > 0) {
                        Text(resources.getString(R.string.calendar_types, numbers.format(selectedDay?.qa ?: 0), numbers.format(selectedDay?.cloze ?: 0)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
                        Text(resources.getString(R.string.calendar_new_overdue, numbers.format(selectedDay?.unseen ?: 0), numbers.format(selectedDay?.overdue ?: 0)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
                        Text(resources.getString(R.string.calendar_preview_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(vertical = RecallSpacing.sm))
                    }
                }
            }
            when {
                countsLoad.failed || cardsLoad.failed -> item {
                    Text(resources.getString(R.string.calendar_error), color = MaterialTheme.colorScheme.error)
                }
                countsLoad.value == null || cardsLoad.value == null -> item {
                    Text(resources.getString(R.string.calendar_loading), modifier = Modifier.padding(vertical = RecallSpacing.lg))
                }
                cards.isEmpty() -> item {
                    RecallEmptyState(Icons.Outlined.CalendarMonth, resources.getString(R.string.calendar_empty), "")
                }
                else -> {
                    items(cards, key = { it.id }) { card ->
                        CalendarCardRow(card, clock, locale) { selectedCardId = card.id }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    item {
                        Text(resources.getString(R.string.calendar_loaded, numbers.format(cards.size), numbers.format(selectedTotal)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted,
                            modifier = Modifier.padding(top = RecallSpacing.sm))
                        if (cards.size < selectedTotal) TextButton({ limit += CalendarSizes.pageSize }) {
                            Text(resources.getString(R.string.calendar_more))
                        }
                    }
                }
            }
        }
    }
    selectedCard?.let { card ->
        RecallSheet({ selectedCardId = null }, expanded = true) {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("calendar-card-details")) {
                BidiAwareText(card.lessonTitle, style = MaterialTheme.typography.titleLarge)
                BidiAwareText(card.subjectName, color = MaterialTheme.colorScheme.muted)
                Text(resources.getString(R.string.calendar_scheduled) + ": " +
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale))
                        .format(Instant.ofEpochMilli(card.dueAt).atZone(clock.zone)),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = RecallSpacing.sm))
                Text(resources.getString(R.string.calendar_question), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                BidiAwareText(card.front, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = RecallSpacing.sm))
                HorizontalDivider()
                Text(resources.getString(R.string.calendar_answer), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.success, modifier = Modifier.padding(top = RecallSpacing.md))
                BidiAwareText(card.back, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = RecallSpacing.sm))
                card.hint?.let { BidiAwareText(it, color = MaterialTheme.colorScheme.muted) }
                card.sourceReference?.let { BidiAwareText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }
                RecallPrimaryButton(resources.getString(R.string.calendar_lesson), onClick = { selectedCardId = null; openLesson(card.lessonId) },
                    modifier = Modifier.fillMaxWidth().padding(top = RecallSpacing.lg))
                TextButton({ selectedCardId = null }, Modifier.fillMaxWidth()) { Text(resources.getString(R.string.calendar_done)) }
            }
        }
    }
}

/** Locale-aware week order; on small screens retain touch targets and allow horizontal scrolling. */
@Composable
internal fun ReviewCalendarMonth(
    month: YearMonth, today: LocalDate, counts: Map<LocalDate, ReviewCalendarDay>, selectedDate: LocalDate,
    locale: Locale, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit,
) {
    val resources = calendarResources(locale)
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val cells = remember(month, locale) { calendarCells(month, locale).chunked(7) }
    val weekdays = remember(locale) { activityWeekdays(locale) }
    val weekdayLabels = remember(resources) { resources.getStringArray(R.array.activity_weekdays) }
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)) }
    Surface(shape = RecallRadii.large, color = MaterialTheme.colorScheme.surfaceElevated, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(tween(RecallMotion.quick)).padding(vertical = RecallSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onMonth(month.minusMonths(1)) }, enabled = month > YearMonth.from(today), modifier = Modifier.testTag("calendar-previous")) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, resources.getString(R.string.calendar_previous))
                }
                Text(DateTimeFormatter.ofPattern("LLLL yyyy", locale).withDecimalStyle(DecimalStyle.of(locale)).format(month), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                IconButton({ onMonth(month.plusMonths(1)) }, modifier = Modifier.testTag("calendar-next")) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, resources.getString(R.string.calendar_next))
                }
            }
            if (month != YearMonth.from(today)) TextButton({ onMonth(YearMonth.from(today)); onDay(today) }) {
                Text(resources.getString(R.string.calendar_today))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val calendarWidth = maxOf(maxWidth, CalendarSizes.dayMinimumWidth * 7)
                Column(Modifier.horizontalScroll(rememberScrollState()).width(calendarWidth)) {
                    Row(Modifier.fillMaxWidth()) {
                        weekdays.forEach { day -> Box(Modifier.weight(1f).padding(vertical = RecallSpacing.xs), contentAlignment = Alignment.Center) {
                            Text(weekdayLabels[day.value - 1], style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.muted, maxLines = 1)
                        } }
                    }
                    cells.forEach { week -> Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            val active = day != null && day >= today
                            val selected = day == selectedDate
                            val count = counts[day]?.total ?: 0
                            val tint by animateColorAsState(when { selected -> MaterialTheme.colorScheme.onPrimary; !active -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onSurface }, tween(RecallMotion.quick), label = "calendarText")
                            val background by animateColorAsState(when {
                                day == null -> androidx.compose.ui.graphics.Color.Transparent
                                selected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surface
                            }, tween(RecallMotion.quick), label = "calendarSelection")
                            val label = day?.let { resources.getString(R.string.calendar_day_label, fullDate.format(it), numbers.format(count)) }.orEmpty()
                            Column(Modifier.weight(1f).heightIn(min = CalendarSizes.dayMinimumHeight).clip(RecallRadii.small)
                                .then(if (day != null) Modifier.testTag("calendar-day-$day") else Modifier)
                                .semantics(mergeDescendants = true) {
                                    this.selected = selected
                                    if (day != null) contentDescription = if (active) label else fullDate.format(day) + ", " + resources.getString(R.string.calendar_past)
                                }
                                .clickable(enabled = active, role = Role.Button) { day?.let(onDay) }
                                .padding(RecallSpacing.xxs).clip(RecallRadii.small).background(background)
                                .then(if (day == today && !selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RecallRadii.small) else Modifier)
                                .padding(vertical = RecallSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Text(day?.let { numbers.format(it.dayOfMonth) }.orEmpty(), style = MaterialTheme.typography.labelLarge, color = tint)
                                if (active) Text(if (count > 0) numbers.format(count) else "—", style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) tint else MaterialTheme.colorScheme.muted)
                            }
                        }
                    } }
                }
            }
        }
    }
}

@Composable private fun CalendarCardRow(card: CardWithLesson, clock: ActivityPeriod, locale: Locale, onClick: () -> Unit) {
    val resources = calendarResources(locale)
    val type = resources.getString(if (card.type == "cloze") R.string.calendar_cloze else R.string.calendar_qa)
    val state = resources.getString(when (card.state) {
        "new" -> R.string.calendar_new; "learning" -> R.string.calendar_learning
        "relearning" -> R.string.calendar_relearning; else -> R.string.calendar_review
    })
    val dueDate = Instant.ofEpochMilli(card.dueAt).atZone(clock.zone)
    val time = if (dueDate.toLocalDate() < clock.today) resources.getString(R.string.calendar_overdue) else
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)).format(dueDate)
    Column(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = RecallSpacing.md).testTag("calendar-card-${card.id}")) {
        BidiAwareText(card.subjectName + " / " + card.lessonTitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BidiAwareText(card.front, style = MaterialTheme.typography.titleMedium, maxLines = 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = RecallSpacing.xxs))
        BidiAwareText("$type · $state · $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
