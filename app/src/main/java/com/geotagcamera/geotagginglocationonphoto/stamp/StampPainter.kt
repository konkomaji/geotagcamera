package com.geotagcamera.geotagginglocationonphoto.stamp

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotagcamera.geotagginglocationonphoto.ui.theme.MonoDataStyle
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Poppins
import com.geotagcamera.geotagginglocationonphoto.ui.theme.RobotoMono
import com.geotagcamera.geotagginglocationonphoto.ui.theme.StampAnchorStyle

/**
 * One draw path for all three stamp templates, shared by both the live
 * viewfinder overlay (a `Canvas` composable, inside composition, Phase 5)
 * and the final bitmap burn-in ([StampRenderer], a `CanvasDrawScope`
 * wrapping the target `android.graphics.Canvas`, outside composition). Both
 * call the same [draw] against the same [StampSpec] — that identity is what
 * makes "what you frame is what burns in" a guarantee, not a coincidence
 * between two separately-maintained implementations.
 *
 * Card/Bar/Minimal are three branches sharing the same row-building idea:
 * nothing is positioned by a fixed index, every row's presence is read
 * straight off [StampSpec], so the layout closes the gap itself when a
 * field is off — never a hole where the address used to be.
 *
 * No RenderEffect/backdrop blur here (that needs API 31+): a flat
 * semi-transparent scrim is used everywhere, matching the design system's
 * own documented low-end/API-26 fallback path (section 10, "API 26
 * fallbacks" — "a slightly darker flat scrim below it, tuned to the same
 * measured contrast"). Address/date lines are measured single-line for now
 * (no wrap); very long addresses may overflow the card edge until this
 * gets a real device pass and a wrap pass is added.
 */
object StampPainter {
    private val CardScrim = Color(0xB2141619) // ~rgba(20,22,25,.70), one shade lighter than chrome/base for legibility
    private val CardBorder = Color(0x24FFFFFF) // rgba(255,255,255,.14)
    private val TextPrimary = Color.White
    private val TextSecondary = Color(0xCCFFFFFF) // rgba(255,255,255,.80)
    private val TextMuted = Color(0x8CFFFFFF) // rgba(255,255,255,.55)
    private val ChipBackground = Color(0x1CFFFFFF) // rgba(255,255,255,.11)
    private val BrandDotColor = Color(0xFF56CB98) // accent/verified

    private val AddressStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = TextSecondary)
    private val DateTimeStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = TextSecondary)
    private val ChipStyle = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = TextPrimary)
    private val MutedChipStyle = ChipStyle.copy(color = TextMuted)
    private val FooterLabelStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary)
    private val CountryChipStyle = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color(0xFF0A0C0E))

    fun draw(scope: DrawScope, spec: StampSpec, textMeasurer: TextMeasurer) {
        when (spec.template) {
            StampTemplate.CARD -> drawCard(scope, spec, textMeasurer)
            StampTemplate.BAR -> drawBar(scope, spec, textMeasurer)
            StampTemplate.MINIMAL -> drawMinimal(scope, spec, textMeasurer)
        }
    }

    // ---- Card: the primary template, a scrim card anchored to one of nine positions ----

    private fun drawCard(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        val px3 = px(3f); val px1 = px(1f)
        val margin = size.minDimension * 0.035f
        val cardWidth = (size.width * 0.62f).coerceAtMost(size.width - margin * 2)
        val pad = cardWidth * 0.045f
        val gap = cardWidth * 0.03f

        val tileSize = if (spec.mapTile != null) cardWidth * 0.26f else 0f
        val hasTile = spec.mapTile != null

        val placeLayout = spec.placeName?.let { tm.measure(it, StampAnchorStyle, maxLines = 1) }
        val countryLayout = spec.countryCode?.let { tm.measure(it, CountryChipStyle, maxLines = 1) }
        val addressLayout = spec.addressLine?.let { tm.measure(it, AddressStyle, maxLines = 1) }
        val coordsLayout = spec.coordinatesText?.let { tm.measure(it, MonoDataStyle, maxLines = 1) }
        val dateTimeText = listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ")
        val dateTimeLayout = dateTimeText.takeIf { it.isNotBlank() }?.let { tm.measure(it, DateTimeStyle, maxLines = 1) }

        val textBlockHeight = stackedHeight(listOfNotNull(placeLayout, addressLayout, coordsLayout, dateTimeLayout), px3)
        val rowHeight = maxOf(tileSize, textBlockHeight)

        val chipRowHeight = if (spec.chips.isNotEmpty()) with(ChipStyle.fontSize) { toPx() } * 2.6f else 0f
        val footerHeight = if (spec.hasFooterRow) with(FooterLabelStyle.fontSize) { toPx() } * 2.4f else 0f

        val cardHeight = pad * 2 +
            rowHeight +
            (if (spec.chips.isNotEmpty()) gap + chipRowHeight else 0f) +
            (if (spec.hasFooterRow) gap + footerHeight else 0f)

        val cardOrigin = anchorOrigin(spec.anchor, Size(cardWidth, cardHeight), margin)
        val corner = CornerRadius(cardWidth * 0.045f)

        drawRoundRect(color = CardScrim, topLeft = cardOrigin, size = Size(cardWidth, cardHeight), cornerRadius = corner)
        drawRoundRect(color = CardBorder, topLeft = cardOrigin, size = Size(cardWidth, cardHeight), cornerRadius = corner, style = Stroke(width = px1))

        var x = cardOrigin.x + pad
        val rowTop = cardOrigin.y + pad

        if (hasTile) {
            val tile = spec.mapTile!!
            val tileCorner = CornerRadius(tileSize * 0.12f)
            clipRoundRect(Offset(x, rowTop), Size(tileSize, tileSize), tileCorner) {
                drawImage(tile, dstOffset = IntOffset(x.toInt(), rowTop.toInt()), dstSize = IntSize(tileSize.toInt(), tileSize.toInt()))
            }
            x += tileSize + gap
        }

        var textY = rowTop
        placeLayout?.let { layout ->
            drawText(layout, topLeft = Offset(x, textY))
            countryLayout?.let { cl ->
                val chipPad = px(4f)
                val chipX = x + layout.size.width + px(7f)
                val chipSize = Size(cl.size.width + chipPad * 2, cl.size.height + chipPad * 1.2f)
                drawRoundRect(Color(0xFFE9EBEC), Offset(chipX, textY + px1), chipSize, CornerRadius(px(3f)))
                drawText(cl, topLeft = Offset(chipX + chipPad, textY + px1 + chipPad * 0.6f))
            }
            textY += layout.size.height + px3
        }
        addressLayout?.let { layout -> drawText(layout, topLeft = Offset(x, textY)); textY += layout.size.height + px3 }
        coordsLayout?.let { layout -> drawText(layout, topLeft = Offset(x, textY)); textY += layout.size.height + px3 }
        dateTimeLayout?.let { layout -> drawText(layout, topLeft = Offset(x, textY)) }

        var y = rowTop + rowHeight

        if (spec.chips.isNotEmpty()) {
            y += gap
            var chipX = cardOrigin.x + pad
            spec.chips.forEach { chip ->
                val layout = tm.measure(chip.text, ChipStyle, maxLines = 1)
                val chipPad = px(5f)
                val chipSize = Size(layout.size.width + chipPad * 2, layout.size.height + chipPad * 1.2f)
                drawRoundRect(ChipBackground, Offset(chipX, y), chipSize, CornerRadius(px(4f)))
                drawText(layout, topLeft = Offset(chipX + chipPad, y + chipPad * 0.6f))
                chipX += chipSize.width + px(5f)
            }
            y += chipRowHeight
        }

        if (spec.hasFooterRow) {
            y += gap
            drawLine(CardBorder, Offset(cardOrigin.x + pad, y), Offset(cardOrigin.x + cardWidth - pad, y), strokeWidth = px1)
            val footerY = y + gap * 0.7f
            var footerX = cardOrigin.x + pad
            val logoSize = footerHeight * 0.85f

            spec.orgLogo?.let { logo ->
                val logoCorner = CornerRadius(logoSize * 0.2f)
                val logoTop = footerY - logoSize * 0.1f
                clipRoundRect(Offset(footerX, logoTop), Size(logoSize, logoSize), logoCorner) {
                    drawImage(logo, dstOffset = IntOffset(footerX.toInt(), logoTop.toInt()), dstSize = IntSize(logoSize.toInt(), logoSize.toInt()))
                }
                footerX += logoSize + gap * 0.6f
            }
            spec.orgLabel?.let { label ->
                val layout = tm.measure(label, FooterLabelStyle, maxLines = 1)
                drawText(layout, topLeft = Offset(footerX, footerY))
            }
            val trailingReserve = (if (spec.showBrandMark) px(56f) else 0f)
            if (spec.showSignedMark) {
                val layout = tm.measure("SIGNED", MutedChipStyle, maxLines = 1)
                drawText(layout, topLeft = Offset(cardOrigin.x + cardWidth - pad - layout.size.width - trailingReserve, footerY))
            }
            if (spec.showBrandMark) drawBrandMark(this, Offset(cardOrigin.x + cardWidth - pad - px(44f), footerY))
        }
    }

    // ---- Bar: a bottom gradient scrim, no card, place+address left, coords+date right ----

    private fun drawBar(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        val px3 = px(3f)
        val pad = size.width * 0.045f
        val placeLayout = spec.placeName?.let { tm.measure(it, StampAnchorStyle, maxLines = 1) }
        val addressLayout = spec.addressLine?.let { tm.measure(it, AddressStyle, maxLines = 1) }
        val coordsLayout = spec.coordinatesText?.let { tm.measure(it, MonoDataStyle, maxLines = 1) }
        val dateTimeText = listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ")
        val dateTimeLayout = dateTimeText.takeIf { it.isNotBlank() }?.let { tm.measure(it, DateTimeStyle, maxLines = 1) }

        val leftHeight = stackedHeight(listOfNotNull(placeLayout, addressLayout), px3)
        val rightHeight = stackedHeight(listOfNotNull(coordsLayout, dateTimeLayout), px3)
        val barHeight = maxOf(leftHeight, rightHeight, 1f) + pad * 1.6f
        if (placeLayout == null && addressLayout == null && coordsLayout == null && dateTimeLayout == null) return@with

        drawRect(
            color = Color.Black.copy(alpha = 0.72f),
            topLeft = Offset(0f, size.height - barHeight),
            size = Size(size.width, barHeight)
        )

        var leftY = size.height - barHeight + pad * 0.8f
        placeLayout?.let { drawText(it, topLeft = Offset(pad, leftY)); leftY += it.size.height + px3 }
        addressLayout?.let { drawText(it, topLeft = Offset(pad, leftY)) }

        var rightY = size.height - barHeight + pad * 0.8f
        coordsLayout?.let { drawText(it, topLeft = Offset(size.width - pad - it.size.width, rightY)); rightY += it.size.height + px3 }
        dateTimeLayout?.let { drawText(it, topLeft = Offset(size.width - pad - it.size.width, rightY)) }
    }

    // ---- Minimal: a small blurred-look pill, coordinates and date/time only ----

    private fun drawMinimal(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        val margin = size.minDimension * 0.035f
        val pad = px(10f)
        val px2 = px(2f)
        val coordsLayout = spec.coordinatesText?.let { tm.measure(it, MonoDataStyle, maxLines = 1) }
        val dateTimeText = listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ")
        val dateTimeLayout = dateTimeText.takeIf { it.isNotBlank() }?.let { tm.measure(it, DateTimeStyle, maxLines = 1) }
        if (coordsLayout == null && dateTimeLayout == null) return@with

        val width = maxOf(coordsLayout?.size?.width ?: 0, dateTimeLayout?.size?.width ?: 0) + pad * 2
        val height = stackedHeight(listOfNotNull(coordsLayout, dateTimeLayout), px2) + pad * 1.4f

        val origin = anchorOrigin(spec.anchor, Size(width, height), margin)
        drawRoundRect(CardScrim, origin, Size(width, height), CornerRadius(px(9f)))

        var y = origin.y + pad * 0.7f
        coordsLayout?.let { drawText(it, topLeft = Offset(origin.x + pad, y)); y += it.size.height + px2 }
        dateTimeLayout?.let { drawText(it, topLeft = Offset(origin.x + pad, y)) }
    }

    // ---- Shared helpers ----

    private fun drawBrandMark(scope: DrawScope, topLeft: Offset) = with(scope) {
        val frameSize = px(12f)
        val stroke = px(1.5f)
        drawRoundRect(
            color = TextPrimary.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = Size(frameSize, frameSize),
            cornerRadius = CornerRadius(frameSize * 0.33f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = BrandDotColor.copy(alpha = 0.55f),
            radius = frameSize * 0.16f,
            center = Offset(topLeft.x + frameSize / 2f, topLeft.y + frameSize / 2f)
        )
    }

    /** Nine-anchor placement: same grid the viewfinder drag and the Settings position picker use. */
    private fun DrawScope.anchorOrigin(anchor: StampAnchor, contentSize: Size, margin: Float): Offset {
        val x = when (anchor) {
            StampAnchor.TOP_LEFT, StampAnchor.MID_LEFT, StampAnchor.BOTTOM_LEFT -> margin
            StampAnchor.TOP_CENTER, StampAnchor.MID_CENTER, StampAnchor.BOTTOM_CENTER -> (size.width - contentSize.width) / 2f
            StampAnchor.TOP_RIGHT, StampAnchor.MID_RIGHT, StampAnchor.BOTTOM_RIGHT -> size.width - contentSize.width - margin
        }
        val y = when (anchor) {
            StampAnchor.TOP_LEFT, StampAnchor.TOP_CENTER, StampAnchor.TOP_RIGHT -> margin
            StampAnchor.MID_LEFT, StampAnchor.MID_CENTER, StampAnchor.MID_RIGHT -> (size.height - contentSize.height) / 2f
            StampAnchor.BOTTOM_LEFT, StampAnchor.BOTTOM_CENTER, StampAnchor.BOTTOM_RIGHT -> size.height - contentSize.height - margin
        }
        return Offset(x, y)
    }

    private fun DrawScope.clipRoundRect(topLeft: Offset, size: Size, corner: CornerRadius, block: DrawScope.() -> Unit) {
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(topLeft, size), corner))
        }
        clipPath(path) { block() }
    }

    /** [value] is in dp; converts to px using this DrawScope's own density. */
    private fun DrawScope.px(value: Float): Float = value.dp.toPx()

    /** Sum of each layout's height plus one [gap] between consecutive items, never after the last. */
    private fun stackedHeight(layouts: List<TextLayoutResult>, gap: Float): Float =
        if (layouts.isEmpty()) 0f else layouts.sumOf { it.size.height } + gap * (layouts.size - 1)
}
