package com.example.myapplication4.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.myapplication4.domain.LessonReviewPause
import com.example.myapplication4.domain.LessonPauseCodec

val Context.recallPreferences by preferencesDataStore("recall_preferences")

data class UserSettings(
    val desiredRetention: Double = .90,
    val newCardLimit: Int = 20,
    val remindersEnabled: Boolean = false,
    val studyWindowReminder: Boolean = true,
    val pausedUntil: Long? = null,
    val themeMode: String = "system",
    val language: String = "system",
    val dynamicColor: Boolean = false,
    val speechRate: Float = 1f,
    val debugMode: Boolean = false,
    val lessonPauses: List<LessonReviewPause> = emptyList(),
)

object PreferenceKeys {
    val lessonPauses = stringPreferencesKey("lesson_review_pauses")
    val introductionSeen = booleanPreferencesKey("introduction_seen")
    val debugMode = booleanPreferencesKey("debug_mode")
    val speechRate = floatPreferencesKey("speech_rate")
    val desiredRetention = doublePreferencesKey("desired_retention")
    val newCardLimit = intPreferencesKey("new_card_limit")
    val remindersEnabled = booleanPreferencesKey("reminders_enabled")
    val studyWindowReminder = booleanPreferencesKey("study_window_reminder")
    val pausedUntil = longPreferencesKey("paused_until")
    val themeMode = stringPreferencesKey("theme_mode")
    val language = stringPreferencesKey("language")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
}

class UserPreferences(private val context: Context) {
    // Device-local onboarding state: restoring a study backup must not reset it.
    val introductionSeen: Flow<Boolean> = context.recallPreferences.data.map { it[PreferenceKeys.introductionSeen] ?: false }
    suspend fun markIntroductionSeen() = update(PreferenceKeys.introductionSeen, true)
    val settings: Flow<UserSettings> = context.recallPreferences.data.map { values ->
        UserSettings(
            desiredRetention = values[PreferenceKeys.desiredRetention] ?: .90,
            newCardLimit = values[PreferenceKeys.newCardLimit] ?: 20,
            remindersEnabled = values[PreferenceKeys.remindersEnabled] ?: false,
            studyWindowReminder = values[PreferenceKeys.studyWindowReminder] ?: true,
            pausedUntil = values[PreferenceKeys.pausedUntil]?.takeIf { it > System.currentTimeMillis() },
            themeMode = values[PreferenceKeys.themeMode] ?: "system",
            language = values[PreferenceKeys.language] ?: "system",
            dynamicColor = values[PreferenceKeys.dynamicColor] ?: false,
            speechRate = values[PreferenceKeys.speechRate]?.takeIf { it in .75f..1.25f } ?: 1f,
            debugMode = values[PreferenceKeys.debugMode] ?: false,
            lessonPauses = LessonPauseCodec.readPreference(values[PreferenceKeys.lessonPauses]),
        )
    }

    suspend fun setLessonPause(lessonId: String, pause: LessonReviewPause?) {
        require(pause == null || pause.lessonId == lessonId)
        context.recallPreferences.edit {
            val remaining = LessonPauseCodec.readPreference(it[PreferenceKeys.lessonPauses]).filter { entry -> entry.lessonId != lessonId && entry.endAt > System.currentTimeMillis() }
            it[PreferenceKeys.lessonPauses] = LessonPauseCodec.encode(remaining + listOfNotNull(pause))
        }
    }
    suspend fun removeLessonPauses(ids: Set<String>) {
        context.recallPreferences.edit {
            it[PreferenceKeys.lessonPauses] = LessonPauseCodec.encode(LessonPauseCodec.readPreference(it[PreferenceKeys.lessonPauses]).filter { pause -> pause.lessonId !in ids })
        }
    }
    suspend fun setDebugMode(value: Boolean) = update(PreferenceKeys.debugMode, value)
    suspend fun setSpeechRate(value: Float) = update(PreferenceKeys.speechRate, value.takeIf { it in .75f..1.25f } ?: 1f)
    suspend fun setRetention(value: Double) = update(PreferenceKeys.desiredRetention, value.coerceIn(.85, .95))
    suspend fun setNewCardLimit(value: Int) = update(PreferenceKeys.newCardLimit, value.coerceIn(0, 1000))
    suspend fun setRemindersEnabled(value: Boolean) = update(PreferenceKeys.remindersEnabled, value)
    suspend fun setStudyWindowReminder(value: Boolean) = update(PreferenceKeys.studyWindowReminder, value)
    suspend fun pauseUntil(value: Long?) { context.recallPreferences.edit { if (value == null) it.remove(PreferenceKeys.pausedUntil) else it[PreferenceKeys.pausedUntil] = value } }
    suspend fun setThemeMode(value: String) = update(PreferenceKeys.themeMode, value)
    suspend fun setLanguage(value: String) = update(PreferenceKeys.language, value)
    suspend fun setDynamicColor(value: Boolean) = update(PreferenceKeys.dynamicColor, value)
    suspend fun restore(value: UserSettings) {
        context.recallPreferences.edit {
            it[PreferenceKeys.desiredRetention] = value.desiredRetention
            it[PreferenceKeys.newCardLimit] = value.newCardLimit
            it[PreferenceKeys.remindersEnabled] = value.remindersEnabled
            it[PreferenceKeys.studyWindowReminder] = value.studyWindowReminder
            if (value.pausedUntil == null) it.remove(PreferenceKeys.pausedUntil) else it[PreferenceKeys.pausedUntil] = value.pausedUntil
            it[PreferenceKeys.themeMode] = value.themeMode
            it[PreferenceKeys.language] = value.language
            it[PreferenceKeys.dynamicColor] = value.dynamicColor
            it[PreferenceKeys.speechRate] = value.speechRate
            it[PreferenceKeys.debugMode] = value.debugMode
            it[PreferenceKeys.lessonPauses] = LessonPauseCodec.encode(value.lessonPauses)
        }
    }

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.recallPreferences.edit { it[key] = value }
    }
}
