package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onOpenPhoto: (Long) -> Unit = {},
    onVerifyExternal: (String) -> Unit = {},
    viewModel: GalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val tileStatus by viewModel.tileStatus.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selecting = selected.isNotEmpty()

    val pickToVerify = rememberPhotoPicker { uri -> onVerifyExternal(uri.toString()) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No photos yet. Capture one, or verify a photo someone sent you.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoTile(
                        photo = photo,
                        status = tileStatus[photo.id] ?: TileStatus.UNKNOWN,
                        isSelected = photo.id in selected,
                        onClick = {
                            if (selecting) selected = selected.toggle(photo.id)
                            else onOpenPhoto(photo.id)
                        },
                        onLongClick = { selected = selected.toggle(photo.id) }
                    )
                }
            }
        }

        if (selecting) {
            SelectionBar(
                count = selected.size,
                onShare = {
                    val uris = photos.filter { it.id in selected }.map { it.filePath }
                    if (uris.isNotEmpty()) shareMultiple(context, uris)
                },
                onCancel = { selected = emptySet() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            ExtendedFloatingActionButton(
                onClick = pickToVerify,
                text = { Text("Verify a photo") },
                icon = { Text("🔎") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: PhotoEntity,
    status: TileStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = Uri.parse(photo.filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val dot = when (status) {
            TileStatus.VERIFIED -> MaterialTheme.colorScheme.primary
            TileStatus.TAMPERED -> MaterialTheme.colorScheme.error
            else -> null
        }
        dot?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(it)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
            Text(
                "✓",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onShare: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Text("$count selected", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onShare) { Text("Share") }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private fun shareMultiple(context: Context, uriStrings: List<String>) {
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uriStrings.map { Uri.parse(it) }))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photos"))
}
