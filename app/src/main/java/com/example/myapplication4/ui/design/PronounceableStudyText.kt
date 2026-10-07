package com.example.myapplication4.ui.design

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.*
import com.example.myapplication4.R
import com.example.myapplication4.domain.*

val LocalPronunciation = staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

@Composable
fun PronounceableStudyText(
    text: String,
    side: String,
    targets: List<PronunciationTarget>,
    lessonLanguage: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val speak = LocalPronunciation.current
    val haptic = LocalHapticFeedback.current
    val resolved = remember(text, side, targets, lessonLanguage) { PronunciationResolver.resolve(text, side, targets, lessonLanguage) }
    val display = remember(text) { studyDisplayText(text) }
    val foreground = if (color == Color.Unspecified) LocalContentColor.current else color
    val pressed = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
    val pronounceLabel = stringResource(R.string.pronounce_word)
    fun activate(target: ResolvedPronunciation) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        speak(target.target.text, target.language)
    }
    val activateLatest by rememberUpdatedState<(ResolvedPronunciation) -> Unit> { activate(it) }
    val base = remember(display) { display.annotated() }
    val annotated = remember(base, resolved, foreground, pressed) { buildAnnotatedString {
        append(base)
        resolved.forEachIndexed { index, target ->
            val range = display.displayRange(target.start, target.end)
            addLink(
                LinkAnnotation.Clickable(
                    tag = "pronunciation-" + index,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = foreground, textDecoration = TextDecoration.Underline),
                        pressedStyle = SpanStyle(background = pressed),
                        focusedStyle = SpanStyle(background = pressed),
                    ),
                    linkInteractionListener = { activateLatest(target) },
                ),
                range.first, range.last + 1,
            )
        }
    } }
    Text(
        annotated,
        modifier.semantics {
            // Links retain natural reading order; named actions explain their purpose to TalkBack.
            customActions = resolved.map { target ->
                CustomAccessibilityAction(pronounceLabel.format(target.target.text)) { activate(target); true }
            }
        },
        color = foreground,
        style = style.copy(textDirection = TextDirection.Content),
        maxLines = maxLines,
    )
}
