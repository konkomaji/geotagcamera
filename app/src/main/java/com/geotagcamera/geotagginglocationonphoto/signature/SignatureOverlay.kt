package com.geotagcamera.geotagginglocationonphoto.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/** Burns a field worker's on-screen signature into the top-right corner of a stamped photo. */
object SignatureOverlay {

    fun apply(photo: Bitmap, signature: Bitmap): Bitmap {
        val output = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val boxWidth = output.width * 0.34f
        val boxHeight = boxWidth * (signature.height.toFloat() / signature.width)
        val margin = output.width * 0.03f
        val left = output.width - boxWidth - margin
        val top = margin

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 0, 0) }
        canvas.drawRoundRect(RectF(left, top, left + boxWidth, top + boxHeight), 16f, 16f, bgPaint)

        val scaled = Bitmap.createScaledBitmap(signature, boxWidth.roundToInt(), boxHeight.roundToInt(), true)
        canvas.drawBitmap(scaled, left, top, null)
        if (scaled !== signature) scaled.recycle()

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = output.width * 0.018f
            setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
        }
        canvas.drawText("Field worker signature", left, top + boxHeight + labelPaint.textSize + 4f, labelPaint)

        return output
    }
}
