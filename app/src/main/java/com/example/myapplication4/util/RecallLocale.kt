package com.example.myapplication4.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import com.example.myapplication4.R
import com.example.myapplication4.domain.Rating
import com.example.myapplication4.domain.formatReviewInterval
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** UI locale is independent of the language of cards, tags, files and TTS targets. */
object RecallLocale {
    val languages = listOf("en", "ar", "es", "fr", "de")
    fun resolve(language: String, system: Locale = Locale.getDefault()): Locale {
        val code = if (language in languages) language else system.language.takeIf { it in languages } ?: "en"
        return Locale.Builder().setLanguage(code).apply {
            if (system.country.isNotEmpty()) setRegion(system.country)
        }.build()
    }
    fun context(context: Context, language: String): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(resolve(language)) },
    )
}

class RecallStrings(val resources: Resources) {
    val locale: Locale get() = resources.configuration.locales[0]
    operator fun invoke(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)
    fun number(value: Number): String = NumberFormat.getIntegerInstance(locale).format(value)
    fun count(@PluralsRes id: Int, count: Int): String = resources.getQuantityString(id, count, number(count))
    fun rating(rating: Rating): String = invoke(when (rating) {
        Rating.AGAIN -> R.string.ui_again
        Rating.HARD -> R.string.ui_hard
        Rating.GOOD -> R.string.ui_good
        Rating.EASY -> R.string.ui_easy
    })
    fun interval(milliseconds: Long): String {
        // Reuse the scheduler's display rounding, without changing scheduling behavior.
        val raw = formatReviewInterval(milliseconds)
        val value = raw.takeWhile { it.isDigit() }.toIntOrNull() ?: return raw
        val id = when (raw.dropWhile { it.isDigit() }) {
            "s" -> R.string.ui_second_interval
            "w" -> R.string.ui_week_interval
            "m" -> R.string.ui_minute_interval
            "h" -> R.string.ui_hour_interval
            "d" -> R.string.ui_day_interval
            "mo" -> R.string.ui_month_interval
            "y" -> R.string.ui_year_interval
            else -> return raw
        }
        return invoke(id, number(value))
    }
    fun date(value: Long, time: Boolean = false): String =
        (if (time) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        else DateFormat.getDateInstance(DateFormat.FULL, locale)).format(Date(value))
    fun weekday(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
}

@Composable fun recallStrings(): RecallStrings {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { RecallStrings(resources) }
}
