package com.example.myapplication4.ui

import com.example.myapplication4.R
import com.example.myapplication4.util.recallStrings
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.SubjectResourceEntity
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*

@Composable
fun SubjectResources(subjectId: String, vm: RecallViewModel) {
    val s = recallStrings()

    val resources by remember(subjectId) { vm.resources(subjectId) }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val activity = LocalActivity.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var editor by remember { mutableStateOf<SubjectResourceEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf<SubjectResourceEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: s(R.string.ui_study_file)
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                vm.saveResource(SubjectResourceEntity(subjectId = subjectId, title = title, kind = if (mime.startsWith("image/")) "photo" else "pdf", uri = uri.toString(), mimeType = mime), false)
            }.onFailure { message = s(R.string.ui_file_access_error) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
            FilledTonalButton({ creating = true; editor = SubjectResourceEntity(subjectId = subjectId, title = "") }, Modifier.weight(1f)) { Icon(Icons.Outlined.EditNote, null); Text(s(R.string.ui_new_note)) }
            FilledTonalButton({ picker.launch(arrayOf("application/pdf", "image/*")) }, Modifier.weight(1f)) { Icon(Icons.Outlined.AttachFile, null); Text(s(R.string.ui_add_files)) }
        }
        Text(s(R.string.ui_resource_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(vertical = RecallSpacing.sm))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text(s(R.string.ui_search_resources)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RecallRadii.medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { listOf("all" to com.example.myapplication4.R.string.ui_all, "note" to com.example.myapplication4.R.string.ui_notes, "pdf" to com.example.myapplication4.R.string.ui_pdfs, "photo" to com.example.myapplication4.R.string.ui_photos).forEach { (kind, label) -> FilterChip(filter == kind, { filter = kind }, { Text(s(label)) }) } }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val visible = resources.filter { (filter == "all" || it.kind == filter) && (it.title.contains(query, true) || it.note.contains(query, true)) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = RecallSpacing.xl)) {
            if (visible.isEmpty()) item { RecallEmptyState(Icons.Outlined.FolderOpen, if(resources.isEmpty()) s(R.string.ui_subject_resources_title) else s(R.string.ui_no_matching_resources), s(R.string.ui_resources_hint)) }
            items(visible, key = { it.id }) { resource ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { BidiAwareText(resource.title, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { BidiAwareText(resource.note.ifBlank { s(when(resource.kind) { "note" -> com.example.myapplication4.R.string.ui_notes; "photo" -> com.example.myapplication4.R.string.ui_photos; else -> com.example.myapplication4.R.string.ui_pdfs }) }, maxLines = 2, color = MaterialTheme.colorScheme.muted) },
                    leadingContent = { Icon(when(resource.kind) { "note" -> Icons.Outlined.Description; "photo" -> Icons.Outlined.Image; else -> Icons.Outlined.PictureAsPdf }, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Box { RecallIconButton(Icons.Outlined.MoreVert, s(R.string.ui_resource_actions)) { menu = true }; DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(s(R.string.ui_edit)) }, { menu = false; creating = false; editor = resource })
                        DropdownMenuItem({ Text(s(R.string.ui_remove), color = MaterialTheme.colorScheme.error) }, { menu = false; deletion = resource })
                    } } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = recallItemMotion().clickable {
                        if(resource.uri == null) { creating = false; editor = resource }
                        else runCatching { (activity ?: context).startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(resource.uri), resource.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }.onFailure { message = s(R.string.ui_file_open_error) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    editor?.let { resource -> ResourceEditor(resource, { vm.saveResource(it, !creating); editor = null }, { editor = null }) }
    deletion?.let { resource -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text(s(R.string.ui_remove_resource_question)) }, text = { Text(if(resource.uri == null) s(R.string.ui_note_delete) else s(R.string.ui_reference_delete)) }, confirmButton = { TextButton({ vm.deleteResource(resource.id); deletion = null }) { Text(s(R.string.ui_remove)) } }, dismissButton = { TextButton({ deletion = null }) { Text(s(R.string.ui_cancel)) } }) }
}

@Composable
private fun ResourceEditor(resource: SubjectResourceEntity, save: (SubjectResourceEntity) -> Unit, dismiss: () -> Unit) {
    val s = recallStrings()

    var title by rememberSaveable(resource.id) { mutableStateOf(resource.title) }
    var note by rememberSaveable(resource.id) { mutableStateOf(resource.note) }
    RecallSheet(dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RecallSpacing.md)) {
            Text(if(resource.kind == "note") s(R.string.ui_study_note) else s(R.string.ui_resource_details), style = MaterialTheme.typography.titleLarge)
            RecallInput(title, { title = it }, s(R.string.ui_title))
            RecallInput(note, { note = it }, if(resource.kind == "note") s(R.string.ui_notes) else s(R.string.ui_file_notes), singleLine = false, minLines = 5)
            RecallPrimaryButton(s(R.string.ui_save), onClick = { save(resource.copy(title = title.trim(), note = note)) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth())
        }
    }
}
