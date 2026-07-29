package com.geotagcamera.geotagginglocationonphoto.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Renders the toggleable field/address/time stamp onto a captured photo. Mutates a copy, returns it. */
object StampRenderer {

    fun stamp(
        source: Bitmap,
        fix: LocationFix,
        address: String?,
        capturedAtEpochMs: Long,
        fields: StampFields
    ): Bitmap {
        val lines = buildLines(fix, address, capturedAtEpochMs, fields)
        if (lines.isEmpty()) return source

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val textSize = output.width * 0.028f
        val padding = output.width * 0.03f
        val lineSpacing = textSize * 1.35f
        val bandHeight = padding * 2 + lineSpacing * lines.size

        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, output.height - bandHeight,
                0f, output.height.toFloat(),
                Color.TRANSPARENT, Color.argb(190, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, output.height - bandHeight, output.width.toFloat(), output.height.toFloat(), gradientPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
        }
        val orgPaint = Paint(textPaint).apply {
            color = Color.parseColor("#7FB0DD") // accent-on-dark, matches docs/branding.md lockup
            isFakeBoldText = true
        }

        var y = output.height - bandHeight + padding + textSize
        lines.forEach { (text, isOrgLine) ->
            canvas.drawText(text, padding, y, if (isOrgLine) orgPaint else textPaint)
            y += lineSpacing
        }

        return output
    }

    private fun buildLines(
        fix: LocationFix,
        address: String?,
        capturedAtEpochMs: Long,
        fields: StampFields
    ): List<Pair<String, Boolean>> {
        val lines = mutableListOf<Pair<String, Boolean>>()

        if (fields.showOrgLabel) lines += fields.orgLabel to true
        if (fields.showAddress && !address.isNullOrBlank()) lines += address to false
        if (fields.showCoordinates) {
            lines += "%.6f, %.6f".format(Locale.US, fix.latitude, fix.longitude) to false
        }
        if (fields.showAltitude && fix.altitudeMeters != null) {
            lines += "Alt: ${fix.altitudeMeters.roundToInt()} m" to false
        }
        if (fields.showAccuracy && fix.accuracyMeters != null) {
            lines += "Accuracy: ±${fix.accuracyMeters.roundToInt()} m" to false
        }
        if (fields.showBearing && fix.bearingDegrees != null) {
            lines += "Bearing: ${fix.bearingDegrees.roundToInt()}°" to false
        }
        if (fields.showTimestamp) {
            val formatted = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                .format(Date(capturedAtEpochMs))
            lines += formatted to false
        }

        return lines
    }
}
