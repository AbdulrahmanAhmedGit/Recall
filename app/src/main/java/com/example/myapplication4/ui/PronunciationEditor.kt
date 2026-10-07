package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.myapplication4.domain.*
import com.example.myapplication4.ui.design.*
import com.example.myapplication4.ui.components.RecallPrimaryButton
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.CardEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun StoredCardEditor(card: CardEntity, language: String?, vm: RecallViewModel, dismiss: () -> Unit) {
    val targets by remember(card.id) { vm.pronunciations(card.id) }
        .collectAsStateWithLifecycle<List<PronunciationTarget>?>(null)
    // Do not construct an empty editor while Room is still loading existing annotations.
    targets?.let { loaded ->
        CardEditorSheet(card, loaded, language, { front, back, hint, type, updated ->
            vm.editCard(card.copy(front = front, back = back, hint = hint, type = type), updated)
            dismiss()
        }, dismiss)
    }
}

@Composable
internal fun PronunciationEditor(
    front: String, back: String, targets: List<PronunciationTarget>,
    lessonLanguage: String?, change: (List<PronunciationTarget>) -> Unit,
) {
    val s = recallStrings()

    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("front") }
    var language by remember { mutableStateOf("") }
    var occurrence by remember { mutableStateOf("1") }
    val keyboard = LocalSoftwareKeyboardController.current
    TextButton({ expanded = !expanded }, Modifier.padding(top = RecallSpacing.sm)) {
        Text(s(R.string.ui_pronunciation_count, s.number(targets.size)) + if (expanded) " −" else " +")
    }
    if (!expanded) return
    Text(s(R.string.ui_pronunciation_instruction), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
    targets.forEachIndexed { index, target ->
        Column(Modifier.fillMaxWidth().padding(top = RecallSpacing.sm)) {
            BidiAwareText(target.text, style = MaterialTheme.typography.titleSmall)
            Text(s(R.string.ui_target_meta, s(if(target.side == "front") R.string.ui_question else R.string.ui_answer), target.language ?: lessonLanguage ?: s(R.string.ui_choose_language), s.number(target.occurrence)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted)
            Row {
                TextButton({
                    editing = index; adding = true; text = target.text; side = target.side
                    language = target.language.orEmpty(); occurrence = target.occurrence.toString()
                }) { Text(s(R.string.ui_edit)) }
                TextButton({ change(targets.filterIndexed { i, _ -> i != index }); editing = null; adding = false }) { Text(s(R.string.ui_remove), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (!adding) TextButton({
        editing = null; adding = true; text = ""; language = ""; occurrence = "1"; side = "front"
    }, enabled = targets.size < 32) { Text(s(R.string.ui_add_pronunciation)) }
    else {
        val candidate = PronunciationTarget(side, text, language.trim().ifBlank { null }, occurrence.toIntOrNull() ?: 0)
        val list = targets.toMutableList().also { if (editing == null) it.add(candidate) else it[editing!!] = candidate }
        val otherValidTargets = PronunciationResolver.validate(front, back, targets.filterIndexed { i, _ -> i != editing }, lessonLanguage).targets
        val valid = PronunciationResolver.validate(front, back, otherValidTargets + candidate, lessonLanguage).skipped == 0
        Row(horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
            FilterChip(side == "front", { side = "front" }, { Text(s(R.string.ui_question)) })
            FilterChip(side == "back", { side = "back" }, { Text(s(R.string.ui_answer)) })
        }
        RecallInput(text, { text = it }, s(R.string.ui_exact_phrase))
        Spacer(Modifier.height(RecallSpacing.xs))
        RecallInput(language, { language = it }, lessonLanguage?.let { s(R.string.ui_language_inherits, it) } ?: s(R.string.ui_language_example))
        Spacer(Modifier.height(RecallSpacing.xs))
        RecallInput(occurrence, { occurrence = it.filter(Char::isDigit).take(4) }, s(R.string.ui_occurrence_input))
        if (!valid && text.isNotEmpty()) Text(s(R.string.ui_target_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton({ change(list); adding = false; editing = null; keyboard?.hide() }, enabled = valid) { Text(if (editing == null) s(R.string.ui_add_target) else s(R.string.ui_save_target)) }
            TextButton({ adding = false; editing = null }) { Text(s(R.string.ui_cancel)) }
        }
    }
    if (targets.isNotEmpty()) {
        Text(s(R.string.ui_tap_preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(top = RecallSpacing.sm))
        PronounceableStudyText(front, "front", targets, lessonLanguage, modifier = Modifier.padding(vertical = RecallSpacing.sm))
        PronounceableStudyText(back, "back", targets, lessonLanguage, color = MaterialTheme.colorScheme.muted)
    }
}
