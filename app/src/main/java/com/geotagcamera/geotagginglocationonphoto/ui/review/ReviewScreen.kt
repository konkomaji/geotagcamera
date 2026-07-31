package com.geotagcamera.geotagginglocationonphoto.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.ui.capture.CaptureUiState

private val ChromeBase = Color(0xFF08090A)
private val Ink = Color(0xFFF5F7F8)
private val Muted = Color(0x6BF5F7F8)
private val AccentVerified = Color(0xFF56CB98)

/**
 * Post-capture review takeover (design section 04). Share is the only filled
 * control — the entire point of the app is getting proof into WhatsApp or a
 * report in seconds. The SIGNED · SHA-256 chip is always present here because
 * signing completes inside the save pipeline, before this screen appears.
 */
@Composable
fun ReviewScreen(
    review: CaptureUiState.Review,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChromeBase)
            .padding(horizontal = 16.dp)
    ) {
        // Top bar: close + SIGNED chip
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("✕", color = Ink, fontSize = 20.sp, modifier = Modifier
                .clip(CircleShape)
                .padding(8.dp)
                .clickable(onClick = onDismiss))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(AccentVerified))
                Text("SIGNED · SHA-256", color = AccentVerified, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Stamped photo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = review.uri,
                contentDescription = "Captured photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1D20))
            )
        }

        // Actions: Retake / Done, then Share (primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.5.dp, Color(0x38FFFFFF))
            ) { Text("Retake", color = Ink) }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.5.dp, Color(0x38FFFFFF))
            ) { Text("Done", color = Ink) }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color(0xFF0C0E10))
        ) { Text("Share", fontWeight = FontWeight.Medium, fontSize = 15.5.sp) }

        Text(
            "${review.filename} · ${ReviewFilename.formatSize(review.sizeBytes)}",
            color = Muted,
            fontSize = 10.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
