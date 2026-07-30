package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun SignatureCaptureDialog(onConfirm: (Bitmap?) -> Unit, onSkip: () -> Unit) {
    val padState = remember { SignaturePadState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Field worker signature") },
        text = {
            Column {
                Text("Sign below to confirm you captured this photo. Skip to save without one.")
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF1A1A1A))
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
                }
                TextButton(onClick = { padState.clear() }) { Text("Clear") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(padState.renderToBitmap(canvasSize.width, canvasSize.height)) },
                enabled = !padState.isEmpty
            ) { Text("Confirm & save") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
}
