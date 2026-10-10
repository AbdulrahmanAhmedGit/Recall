package com.example.myapplication4.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication4.R
import com.example.myapplication4.data.TagEntity
import com.example.myapplication4.ui.design.*
import com.example.myapplication4.util.recallStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonTagChips(tags: List<TagEntity>, edit: (TagEntity) -> Unit, remove: (TagEntity) -> Unit) {
    val s = recallStrings()
    FlowRow(Modifier.fillMaxWidth().testTag("lesson-tags"), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs), verticalArrangement = Arrangement.spacedBy(RecallSpacing.xxs)) {
        tags.forEach { tag ->
            InputChip(selected = false, onClick = { edit(tag) }, modifier = Modifier.testTag("lesson-tag-${tag.id}"),
                label = { BidiAwareText("#${tag.name}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { IconButton({ remove(tag) }, Modifier.size(RecallSizes.touch)) { Icon(Icons.Outlined.Close, s(R.string.ui_remove_tag), Modifier.size(RecallSizes.icon)) } })
        }
    }
}
