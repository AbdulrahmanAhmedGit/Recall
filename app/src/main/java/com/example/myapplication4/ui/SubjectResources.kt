package com.example.myapplication4.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.data.SubjectResourceEntity
import com.example.myapplication4.ui.components.*
import com.example.myapplication4.ui.design.*

@Composable
fun SubjectResources(subjectId: String, vm: RecallViewModel) {
    val resources by remember(subjectId) { vm.resources(subjectId) }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
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
                } ?: "Study file"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                vm.saveResource(SubjectResourceEntity(subjectId = subjectId, title = title, kind = if (mime.startsWith("image/")) "photo" else "pdf", uri = uri.toString(), mimeType = mime), false)
            }.onFailure { message = "Unable to keep access to this file. Choose a file saved on your device." }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) {
            FilledTonalButton({ creating = true; editor = SubjectResourceEntity(subjectId = subjectId, title = "") }, Modifier.weight(1f)) { Icon(Icons.Outlined.EditNote, null); Text("New note") }
            FilledTonalButton({ picker.launch(arrayOf("application/pdf", "image/*")) }, Modifier.weight(1f)) { Icon(Icons.Outlined.AttachFile, null); Text("Add files") }
        }
        Text("Keep files on this device for offline access. Originals stay in their current location.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, modifier = Modifier.padding(vertical = RecallSpacing.sm))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search resources") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RecallRadii.medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RecallSpacing.xs)) { listOf("All", "Notes", "PDFs", "Photos").forEach { label -> FilterChip(filter == label, { filter = label }, { Text(label) }) } }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val visible = resources.filter { (filter == "All" || it.kind == when(filter) { "Notes" -> "note"; "Photos" -> "photo"; else -> "pdf" }) && (it.title.contains(query, true) || it.note.contains(query, true)) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = RecallSpacing.xl)) {
            if (visible.isEmpty()) item { RecallEmptyState(Icons.Outlined.FolderOpen, if(resources.isEmpty()) "Your subject, in one place" else "No matching resources", "Save lesson notes, reference PDFs, and page photos here.") }
            items(visible, key = { it.id }) { resource ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { BidiAwareText(resource.title, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { BidiAwareText(resource.note.ifBlank { resource.kind.uppercase() }, maxLines = 2, color = MaterialTheme.colorScheme.muted) },
                    leadingContent = { Icon(when(resource.kind) { "note" -> Icons.Outlined.Description; "photo" -> Icons.Outlined.Image; else -> Icons.Outlined.PictureAsPdf }, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Box { RecallIconButton(Icons.Outlined.MoreVert, "Resource actions") { menu = true }; DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Edit") }, { menu = false; creating = false; editor = resource })
                        DropdownMenuItem({ Text("Remove", color = MaterialTheme.colorScheme.error) }, { menu = false; deletion = resource })
                    } } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable {
                        if(resource.uri == null) { creating = false; editor = resource }
                        else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(resource.uri), resource.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { message = "Cannot open this file. Check that it still exists and a compatible viewer is installed." }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    editor?.let { resource -> ResourceEditor(resource, { vm.saveResource(it, !creating); editor = null }, { editor = null }) }
    deletion?.let { resource -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("Remove resource?") }, text = { Text(if(resource.uri == null) "This note will be deleted." else "This removes the reference from Recall. The original file is kept.") }, confirmButton = { TextButton({ vm.deleteResource(resource.id); deletion = null }) { Text("Remove") } }, dismissButton = { TextButton({ deletion = null }) { Text("Cancel") } }) }
}

@Composable
private fun ResourceEditor(resource: SubjectResourceEntity, save: (SubjectResourceEntity) -> Unit, dismiss: () -> Unit) {
    var title by rememberSaveable(resource.id) { mutableStateOf(resource.title) }
    var note by rememberSaveable(resource.id) { mutableStateOf(resource.note) }
    RecallSheet(dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RecallSpacing.md)) {
            Text(if(resource.kind == "note") "Study note" else "Resource details", style = MaterialTheme.typography.titleLarge)
            RecallInput(title, { title = it }, "Title")
            RecallInput(note, { note = it }, if(resource.kind == "note") "Notes" else "Notes about this file", singleLine = false, minLines = 5)
            RecallPrimaryButton("Save", onClick = { save(resource.copy(title = title.trim(), note = note)) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth())
        }
    }
}
