package com.geotagcamera.geotagginglocationonphoto.ui.capture

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.data.CapturePreferences
import com.geotagcamera.geotagginglocationonphoto.data.ProjectEntity
import com.geotagcamera.geotagginglocationonphoto.data.ProjectWithCount
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ChromeTokens
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The "what is this shot for" row under the viewfinder: active project and
 * the sticky note. Both are optional; when empty they read as quiet prompts.
 */
@Composable
fun CaptureContextRow(project: ProjectEntity?, note: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, ChromeTokens.GlassBorder), RoundedCornerShape(50))
            .background(ChromeTokens.Glass)
            .clickable(onClickLabel = "Change project and note", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pill(if (project != null) Icons.Outlined.Folder else Icons.Outlined.FolderOff, project?.name ?: "No project", project != null, Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(12.dp))
        Pill(Icons.Outlined.StickyNote2, note.ifBlank { "Add note" }, note.isNotBlank(), Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String, active: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (active) ChromeTokens.Ink else ChromeTokens.InkMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (active) ChromeTokens.Ink else ChromeTokens.InkMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pick the project, write the note, or start a new project, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureContextSheet(
    projects: List<ProjectWithCount>,
    activeProjectId: Long?,
    note: String,
    onSelectProject: (Long?) -> Unit,
    onCreateProject: suspend (String) -> String?,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var noteText by remember { mutableStateOf(note) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun close() { onNoteChange(noteText); onDismiss() }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Spacing.XL).navigationBarsPadding()) {
            Text("Note on the stamp", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(Spacing.S))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it.take(CapturePreferences.MAX_NOTE_CHARS) },
                placeholder = { Text("For example: Pole 12, north wall") },
                singleLine = true,
                trailingIcon = {
                    if (noteText.isNotEmpty()) IconButton(onClick = { noteText = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear note") }
                },
                supportingText = { Text("Stays on every photo until you change it. Burned into the image and covered by the signature.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { close() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.size(Spacing.L))
            Text("Project", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(Spacing.XS))
            val active = projects.filter { !it.archived }
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                item { ProjectRow("No project", null, activeProjectId == null) { onSelectProject(null) } }
                items(active, key = { it.id }) { p ->
                    ProjectRow(p.name, "${p.photoCount} photo" + if (p.photoCount == 1) "" else "s", p.id == activeProjectId) { onSelectProject(p.id) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.S))
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(80); error = null },
                    label = { Text("New project name") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        scope.launchSafely(tag = "Projects") { error = onCreateProject(newName); if (error == null) { creating = false; newName = "" } }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { creating = false; newName = ""; error = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        scope.launchSafely(tag = "Projects") { error = onCreateProject(newName); if (error == null) { creating = false; newName = "" } }
                    }) { Text("Create") }
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.S))
                    Text("New project")
                }
            }
            Spacer(Modifier.size(Spacing.L))
        }
    }
}

@Composable
private fun ProjectRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
