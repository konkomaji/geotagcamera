package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(viewModel: GalleryViewModel = viewModel()) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<PhotoEntity?>(null) }

    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No photos yet. Capture one to see it here.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = Uri.parse(photo.filePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(2.dp)
                    .aspectRatio(1f)
                    .clickable { selected = photo }
            )
        }
    }

    selected?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            viewModel = viewModel,
            onDismiss = {
                selected = null
                viewModel.resetVerification()
            }
        )
    }
}

@Composable
private fun PhotoDetailDialog(photo: PhotoEntity, viewModel: GalleryViewModel, onDismiss: () -> Unit) {
    val verifyStatus by viewModel.verifyStatus.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { viewModel.verify(photo) }, enabled = verifyStatus != VerifyStatus.CHECKING) {
                Text("Verify")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Close") } },
        title = { Text("Capture details") },
        text = {
            Column {
                AsyncImage(
                    model = Uri.parse(photo.filePath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Text(
                    SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                        .format(Date(photo.capturedAtEpochMs)),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text("%.6f, %.6f".format(Locale.US, photo.latitude, photo.longitude))
                photo.address?.let { Text(it) }
                Text(
                    when (verifyStatus) {
                        VerifyStatus.UNCHECKED -> "Not verified yet"
                        VerifyStatus.CHECKING -> "Verifying…"
                        VerifyStatus.VERIFIED -> "✓ Unmodified since capture"
                        VerifyStatus.TAMPERED_OR_UNREADABLE -> "✗ Tampered, or the file couldn't be read"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = when (verifyStatus) {
                        VerifyStatus.VERIFIED -> MaterialTheme.colorScheme.primary
                        VerifyStatus.TAMPERED_OR_UNREADABLE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    )
}
