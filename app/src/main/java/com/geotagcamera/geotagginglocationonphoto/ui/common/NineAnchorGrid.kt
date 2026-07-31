package com.geotagcamera.geotagginglocationonphoto.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor

/** Row-major 3×3 of the nine anchors: [row][col], row = top/mid/bottom, col = left/center/right. */
private val ANCHOR_GRID = arrayOf(
    arrayOf(StampAnchor.TOP_LEFT, StampAnchor.TOP_CENTER, StampAnchor.TOP_RIGHT),
    arrayOf(StampAnchor.MID_LEFT, StampAnchor.MID_CENTER, StampAnchor.MID_RIGHT),
    arrayOf(StampAnchor.BOTTOM_LEFT, StampAnchor.BOTTOM_CENTER, StampAnchor.BOTTOM_RIGHT)
)

/**
 * Maps a point given as fractions of the frame (0..1 each axis) to the anchor
 * whose third of the frame it falls in. Shared by the viewfinder drag-snap and
 * the Settings position picker so both agree on where the nine cells are.
 */
fun anchorForFraction(xFraction: Float, yFraction: Float): StampAnchor {
    val col = when {
        xFraction < 1f / 3f -> 0
        xFraction < 2f / 3f -> 1
        else -> 2
    }
    val row = when {
        yFraction < 1f / 3f -> 0
        yFraction < 2f / 3f -> 1
        else -> 2
    }
    return ANCHOR_GRID[row][col]
}

/**
 * A tap-to-choose 3×3 anchor picker. Not used by Capture (which drags the live
 * stamp directly), but the shared cell math above is; this composable is the
 * Settings-side entry point (Phase 10).
 */
@Composable
fun NineAnchorGrid(
    selected: StampAnchor,
    onSelect: (StampAnchor) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx = 1
    var heightPx = 1
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1); heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSelect(anchorForFraction(offset.x / widthPx, offset.y / heightPx))
                }
            }
    ) {
        for (row in 0..2) {
            for (col in 0..2) {
                val anchor = ANCHOR_GRID[row][col]
                val alignBias = BiasAlignment(
                    horizontalBias = (col - 1).toFloat(),
                    verticalBias = (row - 1).toFloat()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = alignBias
                ) {
                    val isSel = anchor == selected
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                            )
                            .border(
                                width = if (isSel) 0.dp else 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(if (isSel) 7.dp else 5.dp)
                    )
                }
            }
        }
    }
}
