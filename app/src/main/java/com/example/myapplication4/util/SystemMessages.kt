package com.example.myapplication4.util

import com.example.myapplication4.R

/** Presentation adapter for legacy domain diagnostics; never applied to study content. */
fun RecallStrings.importError(message: String): String {
    if (locale.language == "en") return message
    val id = when {
        message.startsWith("This file uses an unsupported") -> R.string.ui_import_version
        message.startsWith("A subject is required") -> R.string.ui_import_subject
        message.startsWith("A lesson title is required") -> R.string.ui_import_lesson
        message == "No cards were found." -> R.string.ui_import_no_cards
        message == "The cards list is empty." -> R.string.ui_import_empty
        message == "No valid question and answer pairs were found." -> R.string.ui_import_pairs
        message == "Paste or open a complete Recall JSON file first." -> R.string.ui_import_paste
        message.startsWith("This is not a complete Recall JSON object") -> R.string.ui_import_object
        message.startsWith("The pasted JSON is incomplete") -> R.string.ui_import_truncated
        message.startsWith("An import can contain at most") -> {
            return invoke(R.string.ui_import_limit, number(Regex("\\d+").find(message)?.value?.toIntOrNull() ?: 500))
        }
        message.startsWith("Card ") && message.contains("science notation marker") -> {
            return invoke(R.string.ui_import_marker, number(Regex("\\d+").find(message)?.value?.toIntOrNull() ?: 0))
        }
        else -> R.string.ui_import_invalid
    }
    return invoke(id)
}

fun RecallStrings.reminderDiagnostic(message: String): String {
    val id = when(message) {
        "Reminders are disabled" -> R.string.ui_reminders_disabled
        "No due cards; background checks remain active" -> R.string.ui_no_due_checks
        "Already reminded; waiting for the next day or study window" -> R.string.ui_already_reminded
        "Reminders disabled or paused" -> R.string.ui_disabled_paused
        "Background check failed; retry scheduled" -> R.string.ui_check_failed
        "Allowed" -> R.string.ui_policy_allowed
        "Waiting for an allowed study time" -> R.string.ui_policy_waiting
        "No study window soon; next allowed daytime" -> R.string.ui_policy_fallback
        "Quiet times cover all available times" -> R.string.ui_policy_quiet
        "Next allowed daytime" -> R.string.ui_policy_daytime
        "Notifications are blocked in Android settings" -> R.string.ui_notification_blocked
        "The review reminder channel is blocked" -> R.string.ui_channel_blocked
        "Notification permission was denied" -> R.string.ui_notification_permission_error
        else -> if (message.startsWith("Posted reminder for ")) {
            return invoke(R.string.ui_posted_due, number(Regex("\\d+").find(message)?.value?.toIntOrNull() ?: 0))
        } else return message
    }
    return invoke(id)
}
