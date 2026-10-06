package com.example.myapplication4.ui.design

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication4.domain.ScienceTextProcessor
import com.example.myapplication4.domain.StudyDisplayText

fun studyDisplayText(value: String): StudyDisplayText = ScienceTextProcessor.process(value)
fun isolateStudyText(value: String): String = studyDisplayText(value).text

fun StudyDisplayText.annotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    scienceRanges.forEach { range ->
        addStyle(SpanStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium), range.start, range.end)
    }
}

@Composable
fun BidiAwareText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    val display = remember(text) { studyDisplayText(text) }
    Text(display.annotated(), modifier, style = style.copy(textDirection = TextDirection.Content), color = resolvedColor, maxLines = maxLines, overflow = overflow)
}
