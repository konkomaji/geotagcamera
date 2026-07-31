package com.geotagcamera.geotagginglocationonphoto.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import java.util.Locale

@Composable
fun PhotoDetailScreen(
    photoId: Long,
    onBack: () -> Unit,
    viewModel: PhotoDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(photoId) { viewModel.load(photoId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            state.photo?.let { photo ->
                TextButton(onClick = { shareImage(context, photo.filePath) }) { Text("Share") }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.photo == null -> Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) {
                Text("This photo is no longer available.")
            }
            else -> {
                val photo = state.photo!!
                AsyncImage(
                    model = Uri.parse(photo.filePath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(Modifier.height(14.dp))
                state.outcome?.let { OutcomeChip(it) }

                Spacer(Modifier.height(14.dp))
                // Live EXIF (read at view time), then the details captured with the row.
                state.exifDateTime?.let { MetaRow("Taken", it) }
                val lat = state.exifLat
                val lng = state.exifLng
                if (lat != null && lng != null) {
                    MetaRow("Coordinates", "%.6f, %.6f".format(Locale.US, lat, lng))
                }
                photo.address?.let { MetaRow("Address", it) }
                photo.orgLabel?.let { MetaRow("Organisation", it) }
                if (photo.fieldWorkerSignature) MetaRow("Signature", "Field worker signature attached")
                MetaRow("Source", "Metadata read live from the file's EXIF")
            }
        }
    }
}

@Composable
private fun OutcomeChip(outcome: VerificationOutcome) {
    val (accent, label) = when (outcome) {
        is VerificationOutcome.Untampered -> MaterialTheme.colorScheme.primary to "Untampered since capture"
        is VerificationOutcome.Edited -> MaterialTheme.colorScheme.error to "Edited since capture"
        is VerificationOutcome.NoProof -> MaterialTheme.colorScheme.onSurfaceVariant to "No proof embedded"
        is VerificationOutcome.Unreadable -> MaterialTheme.colorScheme.onSurfaceVariant to "Couldn't read this file"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(accent))
        Text(label, color = accent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
    }
}

private fun shareImage(context: Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photo"))
}
