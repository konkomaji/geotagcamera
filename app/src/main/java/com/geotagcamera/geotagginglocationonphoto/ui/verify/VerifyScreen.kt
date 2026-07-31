package com.geotagcamera.geotagginglocationonphoto.ui.verify

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker
import androidx.compose.runtime.LaunchedEffect

/**
 * Verify a photo from any source — a share-target file, a gallery pick — with
 * zero captures ever taken here. Reads the embedded proof and checks it against
 * the public key that travels in the file itself (design section 05).
 */
@Composable
fun VerifyScreen(
    uri: String?,
    onBack: () -> Unit,
    viewModel: VerifyViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uri) {
        if (!uri.isNullOrBlank()) viewModel.verify(Uri.parse(uri))
    }

    val pickPhoto = rememberPhotoPicker { picked -> viewModel.verify(picked) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
        }
        Text(
            "Verify a photo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        Text(
            "Check whether a photo still matches the proof it was signed with. Works on any image, even one you didn't take.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                is VerifyUiState.Empty -> EmptyPrompt()
                is VerifyUiState.Verifying -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is VerifyUiState.Done -> ResultBlock(s)
            }
        }

        Button(
            onClick = pickPhoto,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("Choose a photo to verify") }
    }
}

@Composable
private fun EmptyPrompt() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            "Pick a photo below to check it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultBlock(done: VerifyUiState.Done) {
    val (accent, title, detail) = when (val o = done.outcome) {
        is VerificationOutcome.Untampered ->
            Triple(MaterialTheme.colorScheme.primary, "Untampered", "Signed ${o.payload.timestampIso}. The image matches its proof exactly.")
        is VerificationOutcome.Edited ->
            Triple(MaterialTheme.colorScheme.error, "Edited since capture", "A proof is present, but the image no longer matches it.")
        is VerificationOutcome.NoProof ->
            Triple(MaterialTheme.colorScheme.onSurfaceVariant, "No proof found", "This photo carries no GeoTag proof. That isn't a failure — it just wasn't signed by this app.")
        is VerificationOutcome.Unreadable ->
            Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Couldn't read this file", "The file couldn't be opened or decoded.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(
            model = done.previewUri,
            contentDescription = "Photo being verified",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(accent))
                Text(title, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }
}
