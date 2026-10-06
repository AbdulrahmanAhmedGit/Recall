package com.example.myapplication4.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.myapplication4.R
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.design.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private object ActivitySizes {
    const val visibleWeeks = 16
    val minimumWeekWidth = 16.dp
    val maximumWeekWidth = 28.dp
    val weekdayWidth = 32.dp
    val rowHeight = 24.dp
    val cellCorner = RoundedCornerShape(3.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyActivityCard(
    activity: StudyActivity,
    modifier: Modifier = Modifier,
    locale: Locale = LocalConfiguration.current.locales[0],
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Recall's language setting currently changes layout direction, not Android resources.
    // Scope translated resources to this component without changing the rest of the app.
    val resources = remember(context, locale, configuration) {
        context.createConfigurationContext(Configuration(configuration).apply { setLocale(locale) }).resources
    }
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val weeks = remember(activity.period, activity.counts, locale) { activity.weeks(locale) }
    val weekdays = remember(locale) { activityWeekdays(locale) }
    val weekdayLabels = remember(resources) { resources.getStringArray(R.array.activity_weekdays) }
    val palette = activityPalette()
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedDate?.let { DailyActivity(LocalDate.parse(it), activity.counts[LocalDate.parse(it)] ?: 0) }
    fun reviewLabel(count: Int) = if (count == 0) resources.getString(R.string.activity_no_reviews)
        else resources.getQuantityString(R.plurals.activity_reviews, count, numberFormat.format(count))

    Surface(modifier.fillMaxWidth().testTag("study-activity"), shape = RecallRadii.large,
        color = MaterialTheme.colorScheme.surfaceElevated, tonalElevation = 0.dp) {
        Column(Modifier.padding(RecallSpacing.md), verticalArrangement = Arrangement.spacedBy(RecallSpacing.sm)) {
            Column {
                Text(resources.getString(R.string.activity_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(resources.getString(R.string.activity_period), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.muted)
            }
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            // Only the time axis stays LTR. Header, localized text and sheet retain app direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val labelWidth = ActivitySizes.weekdayWidth * fontScale
                    val weekWidth = ((maxWidth - labelWidth) / ActivitySizes.visibleWeeks)
                        .coerceIn(ActivitySizes.minimumWeekWidth, ActivitySizes.maximumWeekWidth)
                    val rowHeight = ActivitySizes.rowHeight * fontScale
                    val scroll = rememberScrollState()
                    var initiallyPositioned by rememberSaveable(activity.period.firstDate.toString(), locale.toLanguageTag()) { mutableStateOf(false) }
                    LaunchedEffect(scroll.maxValue) {
                        if (!initiallyPositioned && scroll.maxValue > 0) {
                            scroll.scrollTo(scroll.maxValue)
                            initiallyPositioned = true
                        }
                    }
                    Row {
                        Column(Modifier.width(labelWidth)) {
                            Spacer(Modifier.height(rowHeight))
                            weekdays.forEachIndexed { index, weekday ->
                                Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    if (index % 2 == 0) Text(
                                        if (locale.language == "ar") weekdayLabels[weekday.value - 1]
                                        else weekday.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted,
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f).horizontalScroll(scroll).testTag("activity-timeline")) {
                            Box(Modifier.width(weekWidth * weeks.size).height(rowHeight)) {
                                weeks.forEachIndexed { index, week ->
                                    week.monthStart?.let { month ->
                                        val nearEnd = index > weeks.size - 4
                                        Text(monthFormat.format(month), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.muted, maxLines = 1,
                                            textAlign = if (nearEnd) TextAlign.End else TextAlign.Start,
                                            modifier = Modifier.offset(x = weekWidth * if (nearEnd) weeks.size - 4 else index).width(weekWidth * 4))
                                    }
                                }
                            }
                            Row {
                                weeks.forEach { week ->
                                    key(week.start) {
                                        Column {
                                            week.days.forEach { day ->
                                                if (day == null) Spacer(Modifier.width(weekWidth).height(rowHeight))
                                                else {
                                                    val description = "${fullDate.format(day.date)}, ${reviewLabel(day.reviewCount)}"
                                                    Box(Modifier.width(weekWidth).height(rowHeight)
                                                        .testTag("activity-day-${day.date}")
                                                        .semantics { contentDescription = description }
                                                        .clickable(role = Role.Button) { selectedDate = day.date.toString() },
                                                        contentAlignment = Alignment.Center) {
                                                        Box(Modifier.size(weekWidth - RecallSpacing.xxs)
                                                            .background(palette[day.level], ActivitySizes.cellCorner)
                                                            .then(if (day.date == activity.period.today) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, ActivitySizes.cellCorner) else Modifier))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(resources.getString(R.string.activity_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted)
                    Spacer(Modifier.width(RecallSpacing.xs))
                    palette.forEach { color ->
                        Box(Modifier.padding(horizontal = 2.dp).size(RecallSpacing.sm).background(color, ActivitySizes.cellCorner))
                    }
                    Spacer(Modifier.width(RecallSpacing.xs))
                    Text(resources.getString(R.string.activity_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.muted)
                }
            }
            Text(resources.getString(if (activity.totalReviews == 0) R.string.activity_empty else R.string.activity_scroll_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
        }
    }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedDate = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = RecallSpacing.lg).padding(bottom = RecallSpacing.xl)
                .testTag("activity-day-details"), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
                Text(fullDate.format(selected.date), style = MaterialTheme.typography.titleLarge)
                Text(reviewLabel(selected.reviewCount), style = MaterialTheme.typography.bodyLarge)
                TextButton({ selectedDate = null }, Modifier.align(Alignment.End)) {
                    Text(resources.getString(R.string.activity_close))
                }
            }
        }
    }
}

@Composable
private fun activityPalette(): List<Color> {
    val colors = MaterialTheme.colorScheme
    return listOf(colors.surfaceInteractive,
        lerp(colors.surfaceInteractive, colors.primary, .24f),
        lerp(colors.surfaceInteractive, colors.primary, .47f),
        lerp(colors.surfaceInteractive, colors.primary, .72f), colors.primary)
}
