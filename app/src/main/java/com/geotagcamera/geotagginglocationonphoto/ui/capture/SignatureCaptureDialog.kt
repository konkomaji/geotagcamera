package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Field-worker sign-off as a bottom sheet over the dimmed-but-visible
 * viewfinder (design system section 03/06), not a modal that hides the frame.
 * Split is 2:1 — "Attach & capture" is the primary, twice the width of "Skip"
 * — and the "Stored on this device only" line answers the obvious privacy
 * question inline. The drawing/gesture logic ([SignaturePadState]) is unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCaptureDialog(onConfirm: (Bitmap?) -> Unit, onSkip: () -> Unit) {
    val padState = remember { SignaturePadState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onSkip, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                "Field worker signature",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Sign below to confirm you captured this photo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF14171A), RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> padState.startStroke(offset) },
                            onDrag = { change, _ -> padState.appendPoint(change.position) }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    padState.strokes.forEach { points ->
                        for (i in 1 until points.size) {
                            drawLine(
                                color = Color.White,
                                start = points[i - 1],
                                end = points[i],
                                strokeWidth = 6f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { padState.clear() },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) { Text("Clear", color = Color.White) }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Stored on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Skip") }
                Button(
                    onClick = { onConfirm(padState.renderToBitmap(canvasSize.width, canvasSize.height)) },
                    enabled = !padState.isEmpty,
                    modifier = Modifier.weight(2f).height(52.dp)
                ) { Text("Attach & capture") }
            }
        }
    }
}
