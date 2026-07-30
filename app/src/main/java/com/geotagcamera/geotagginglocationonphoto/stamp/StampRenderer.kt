package com.geotagcamera.geotagginglocationonphoto.stamp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix

/**
 * Burns a [StampSpec] into a captured photo. Building the spec
 * ([buildStampSpec]) is pure data resolution; the actual drawing is
 * [StampPainter.draw], the exact same function Phase 5's live viewfinder
 * overlay calls from inside a Compose `Canvas` — this function's only job
 * is to drive that same draw call outside of any Composition, against a
 * `CanvasDrawScope` wrapping the target bitmap's own `android.graphics.Canvas`.
 *
 * Density note, easy to get subtly wrong: [StampPainter] uses `.dp`/`.sp`
 * sizing for small fixed details (padding, stroke width, font size), which
 * are density-relative by design in Compose. If this burn-in used the real
 * device's screen density, a "12sp" label would come out a completely
 * different *proportion* of a high-resolution sensor photo than it did on
 * the (much lower-resolution) live preview, breaking the whole "what you
 * frame is what burns in" guarantee at exactly the most visible level: text
 * size. The fix is a synthetic density scaled to the image's own pixel
 * width against a fixed reference (360dp, a common phone width baseline),
 * so "the image is 360dp wide" is true regardless of whether the image is
 * actually 1080px (a live preview) or 4032px (a full-resolution capture).
 * Phase 5's live-overlay `Canvas` composable must use this same synthetic
 * density, not `LocalDensity.current`, or the two will drift apart again.
 */
object StampRenderer {
    private const val REFERENCE_WIDTH_DP = 360f

    fun stamp(
        context: Context,
        source: Bitmap,
        fix: LocationFix,
        addressParts: AddressParts?,
        capturedAtEpochMs: Long,
        fields: StampFields,
        mapTile: Bitmap? = null,
        orgLogo: Bitmap? = null,
        hasSignature: Boolean = false
    ): Bitmap {
        val spec = buildStampSpec(fix, addressParts, capturedAtEpochMs, fields, mapTile, orgLogo, hasSignature)

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val density = Density(density = output.width / REFERENCE_WIDTH_DP, fontScale = 1f)
        val fontFamilyResolver = createFontFamilyResolver(context)
        val textMeasurer = TextMeasurer(
            defaultFontFamilyResolver = fontFamilyResolver,
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr
        )

        val canvasDrawScope = CanvasDrawScope()
        canvasDrawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(android.graphics.Canvas(output)),
            size = Size(output.width.toFloat(), output.height.toFloat())
        ) {
            StampPainter.draw(this, spec, textMeasurer)
        }

        return output
    }
}
