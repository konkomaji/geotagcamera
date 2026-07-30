package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset

/** Holds finger-drawn strokes for the signature pad and rasterizes them on confirm. */
@Stable
class SignaturePadState {
    val strokes = mutableStateListOf<List<Offset>>()
    private var current = mutableListOf<Offset>()

    val isEmpty: Boolean get() = strokes.isEmpty()

    fun startStroke(offset: Offset) {
        current = mutableListOf(offset)
        strokes.add(current)
    }

    fun appendPoint(offset: Offset) {
        current.add(offset)
        strokes[strokes.lastIndex] = current.toList()
    }

    fun clear() {
        strokes.clear()
        current = mutableListOf()
    }

    /** Renders strokes onto a transparent bitmap at the pad's on-screen pixel size. */
    fun renderToBitmap(width: Int, height: Int): Bitmap? {
        if (strokes.isEmpty() || width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = width * 0.01f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        strokes.forEach { points ->
            if (points.size < 2) return@forEach
            val path = android.graphics.Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }
}
