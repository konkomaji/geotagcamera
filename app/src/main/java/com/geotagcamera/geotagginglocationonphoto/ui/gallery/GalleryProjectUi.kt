package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.data.ProjectWithCount
import com.geotagcamera.geotagginglocationonphoto.export.ExportFormat
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Filter chips over the grid: All, each active project, No project. */
@Composable
fun ProjectFilterRow(projects: List<ProjectWithCount>, filter: GalleryFilter, onFilter: (GalleryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        item { FilterChip(selected = filter == GalleryFilter.All, onClick = { onFilter(GalleryFilter.All) }, label = { Text("All") }) }
        items(projects.filter { !it.archived || (filter as? GalleryFilter.Project)?.id == it.id }, key = { it.id }) { p ->
            FilterChip(
                selected = (filter as? GalleryFilter.Project)?.id == p.id,
                onClick = { onFilter(GalleryFilter.Project(p.id)) },
                label = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
        item {
            FilterChip(
                selected = filter == GalleryFilter.Unfiled,
                onClick = { onFilter(GalleryFilter.Unfiled) },
                label = { Text("No project") },
                leadingIcon = { Icon(Icons.Outlined.FolderOff, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

/** Pick CSV, KML or GeoJSON, with a hint of what opens each. */
@Composable
fun ExportFormatDialog(count: Int, onPick: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export $count location" + if (count == 1) "" else "s") },
        text = {
            Column {
                ExportFormat.entries.forEach { f ->
                    ListItem(
                        headlineContent = { Text(f.label) },
                        supportingContent = { Text(f.hint) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(f) }
                    )
                }
                Text(
                    "Includes time, coordinates, place, note, project and each photo's SHA-256. Photos themselves are not included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.S)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Export/report progress, failure, or the ready file. */
@Composable
fun ExportProgressDialog(state: ExportState, onShare: (ExportState.Ready) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ExportState.Idle -> Unit
        is ExportState.Working -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
            title = { Text(state.label) },
            text = {
                Column {
                    if (state.total > 0) {
                        LinearProgressIndicator(progress = { state.done / state.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.size(Spacing.S))
                        Text("${state.done} of ${state.total} photos, each re-verified", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Export failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        is ExportState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ready") },
            text = { Text("${state.title} is ready to share or save.") },
            confirmButton = { TextButton(onClick = { onShare(state) }) { Text("Share or save") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}

/** Move the selected photos into a project (or out of all projects), or a new one. */
@Composable
fun MoveToProjectDialog(
    projects: List<ProjectWithCount>,
    count: Int,
    onMove: (Long?) -> Unit,
    onCreate: suspend (String) -> String?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $count photo" + if (count == 1) "" else "s") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(projects.filter { !it.archived }, key = { it.id }) { p ->
                        ListItem(
                            leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onMove(p.id) }
                        )
                    }
                    item {
                        ListItem(
                            leadingContent = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                            headlineContent = { Text("No project") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onMove(null) }
                        )
                    }
                }
                HorizontalDivider()
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80); error = null },
                        label = { Text("New project name") },
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                    )
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.S))
                        Text("New project")
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { scope.launchSafely(tag = "Projects") { error = onCreate(name); if (error == null) onDismiss() } }) { Text("Create and move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Rename, archive or delete projects. Deleting never deletes photos. */
@Composable
fun ManageProjectsDialog(
    projects: List<ProjectWithCount>,
    onRename: (Long, String) -> Unit,
    onArchive: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var renaming by remember { mutableStateOf<ProjectWithCount?>(null) }
    var deleting by remember { mutableStateOf<ProjectWithCount?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projects") },
        text = {
            if (projects.isEmpty()) {
                Text("No projects yet. Create one from the camera screen, under the viewfinder.")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(projects, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${p.photoCount} photo" + (if (p.photoCount == 1) "" else "s") + if (p.archived) " · archived" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renaming = p }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename ${p.name}") }
                            IconButton(onClick = { onArchive(p.id, !p.archived) }) {
                                Icon(if (p.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = if (p.archived) "Unarchive" else "Archive")
                            }
                            IconButton(onClick = { deleting = p }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete ${p.name}") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(80) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(p.id, text); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Only the project is deleted. Its ${p.photoCount} photos stay in your gallery, without a project.") },
            confirmButton = { TextButton(onClick = { onDelete(p.id); deleting = null }) { Text("Delete project") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

