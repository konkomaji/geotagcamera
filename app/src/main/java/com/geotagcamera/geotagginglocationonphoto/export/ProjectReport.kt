package com.geotagcamera.geotagginglocationonphoto.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.R
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.location.map.MapEngine
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * A printable site report: a cover with an overview map and an index, then
 * two photos per A4 page with when, where, the note, and whether each file
 * still verifies against its signature right now (re-checked while building,
 * not copied from the database).
 */
class ProjectReport(private val context: Context) {

    data class Input(
        val title: String,
        val subtitle: String?,
        val organisation: String?,
        val photos: List<PhotoEntity>
    )

    /** Builds the PDF into [out]. [onProgress] gets (done, total) photos. Throws only on I/O failure. */
    suspend fun build(input: Input, out: File, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val photos = input.photos.sortedBy { it.capturedAtEpochMs }
        val overview = runCatching {
            MapEngine(context).overview(photos.map { it.latitude to it.longitude }, MAP_W_PX, MAP_H_PX)
        }.getOrNull()

        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            try {
                val totalPages = 1 + (photos.size + PER_PAGE - 1) / PER_PAGE
                var pageNo = 1
                doc.page(pageNo) { c -> drawCover(c, input, photos, overview); footer(c, pageNo, totalPages) }
                overview?.recycle()

                photos.chunked(PER_PAGE).forEachIndexed { chunkIndex, chunk ->
                    coroutineContext.ensureActive()
                    pageNo++
                    val page = pageNo
                    doc.page(page) { c ->
                        chunk.forEachIndexed { i, photo ->
                            val number = chunkIndex * PER_PAGE + i + 1
                            val top = (MARGIN + i * ((PAGE_H - MARGIN * 2 - FOOTER) / PER_PAGE)).toFloat()
                            drawPhotoBlock(c, photo, number, top)
                            onProgress(number, photos.size)
                        }
                        footer(c, page, totalPages)
                    }
                }
                out.outputStream().use { doc.writeTo(it) }
            } finally {
                doc.close()
            }
        }
    }

    private inline fun PdfDocument.page(number: Int, draw: (Canvas) -> Unit) {
        val page = startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        try { draw(page.canvas) } finally { finishPage(page) }
    }

    // ---- Cover ----

    private fun drawCover(c: Canvas, input: Input, photos: List<PhotoEntity>, overview: Bitmap?) {
        var y = MARGIN.toFloat()
        input.organisation?.takeIf { it.isNotBlank() }?.let {
            y = text(c, it.uppercase(Locale.getDefault()), MARGIN.toFloat(), y, CONTENT_W, label, 1)
            y += 6
        }
        y = text(c, input.title, MARGIN.toFloat(), y, CONTENT_W, title, 2)
        input.subtitle?.let { y = text(c, it, MARGIN.toFloat(), y + 2, CONTENT_W, muted, 2) }
        y += 10

        val range = if (photos.isEmpty()) "No photos" else {
            val first = dateOnly(photos.first().capturedAtEpochMs)
            val last = dateOnly(photos.last().capturedAtEpochMs)
            (if (first == last) first else "$first to $last") + "  ·  ${photos.size} photo" + if (photos.size == 1) "" else "s"
        }
        y = text(c, range, MARGIN.toFloat(), y, CONTENT_W, body, 1)
        y += 12

        if (overview != null) {
            val h = CONTENT_W * overview.height / overview.width.toFloat()
            val dst = RectF(MARGIN.toFloat(), y, (MARGIN + CONTENT_W).toFloat(), y + h)
            c.drawBitmap(overview, null, dst, bitmapPaint)
            c.drawRect(dst, hairline)
            y += h + 14
        }

        y = text(c, "Index", MARGIN.toFloat(), y, CONTENT_W, heading, 1) + 4
        val bottom = PAGE_H - MARGIN - FOOTER
        for ((i, p) in photos.withIndex()) {
            if (y > bottom - 14) {
                text(c, "… and ${photos.size - i} more on the following pages.", MARGIN.toFloat(), y, CONTENT_W, muted, 1)
                break
            }
            val line = "${i + 1}.  ${timeShort(p.capturedAtEpochMs)}  ·  " +
                listOfNotNull(p.note, p.placeName ?: coords(p)).joinToString("  ·  ")
            y = text(c, line, MARGIN.toFloat(), y, CONTENT_W, small, 1) + 2
        }
    }

    // ---- One photo block (half a page) ----

    private fun drawPhotoBlock(c: Canvas, p: PhotoEntity, number: Int, top: Float) {
        val blockH = (PAGE_H - MARGIN * 2 - FOOTER) / PER_PAGE - 14f
        val imageW = CONTENT_W * 0.58f
        val imageH = blockH
        var missingReason = "Photo file not found on this phone"
        val bmp = try {
            loadScaled(Uri.parse(p.filePath), (imageW * 2).toInt(), (imageH * 2).toInt())
        } catch (e: java.io.FileNotFoundException) {
            null
        } catch (e: SecurityException) {
            missingReason = "Photo not readable: access to it was removed"; null
        } catch (e: OutOfMemoryError) {
            missingReason = "Photo not shown: not enough memory to include it"; null
        } catch (e: Exception) {
            missingReason = "Photo couldn't be read"; null
        }
        val left = MARGIN.toFloat()
        if (bmp != null) {
            val scale = minOf(imageW / bmp.width, imageH / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val dst = RectF(left, top, left + w, top + h)
            c.drawBitmap(bmp, null, dst, bitmapPaint)
            c.drawRect(dst, hairline)
            bmp.recycle()
        } else {
            val dst = RectF(left, top, left + imageW, top + imageH * 0.6f)
            c.drawRect(dst, placeholder)
            text(c, missingReason, left + 10, top + 10, imageW - 20, muted, 2)
        }

        val x = left + imageW + 14
        val w = CONTENT_W - imageW - 14
        var y = top
        y = text(c, "#$number", x, y, w, label, 1) + 2
        y = text(c, p.note ?: p.placeName ?: "Photo $number", x, y, w, heading, 3) + 6
        y = fact(c, "Taken", fullTime(p.capturedAtEpochMs), x, y, w)
        p.placeName?.let { y = fact(c, "Place", it, x, y, w) }
        p.address?.let { y = fact(c, "Address", it, x, y, w) }
        y = fact(c, "Coordinates", coords(p), x, y, w)
        val extras = listOfNotNull(
            p.accuracyMeters?.let { "±%.0f m".format(Locale.US, it) },
            p.altitudeMeters?.let { "alt %.0f m".format(Locale.US, it) },
            p.bearingDegrees?.let { "%.0f°".format(Locale.US, it) }
        )
        if (extras.isNotEmpty()) y = fact(c, "GPS", extras.joinToString("  ·  "), x, y, w)
        if (p.fieldWorkerSignature) y = fact(c, "Signature", "Field worker signed", x, y, w)

        val (status, color) = verificationLine(p)
        y += 4
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawCircle(x + 4, y + 7, 4f, dot)
        y = text(c, status, x + 14, y, w - 14, body, 3) + 4
        text(c, "SHA-256 ${p.sha256Hash.take(16)}…", x, y, w, mono, 1)
    }

    private fun verificationLine(p: PhotoEntity): Pair<String, Int> =
        when (val outcome = runCatching { PhotoVerification.verifyUri(context, Uri.parse(p.filePath)) }.getOrDefault(VerificationOutcome.Unreadable)) {
            is VerificationOutcome.Untampered -> "Verified: unchanged since capture" to GREEN
            is VerificationOutcome.MetadataUnavailable -> "Image intact; location metadata missing, not checked" to AMBER
            is VerificationOutcome.Edited -> "Changed since capture: does not match its signature" to RED
            VerificationOutcome.NoProof -> "No GeoTag proof in this file" to GREY
            VerificationOutcome.Unreadable -> "File missing or unreadable, not verified" to GREY
        }

    private fun fact(c: Canvas, label: String, value: String, x: Float, y: Float, w: Float): Float {
        val ny = text(c, label.uppercase(Locale.getDefault()), x, y, w, this.label, 1)
        return text(c, value, x, ny, w, small, 3) + 4
    }

    private fun footer(c: Canvas, page: Int, total: Int) {
        val y = (PAGE_H - MARGIN + 6).toFloat()
        c.drawLine(MARGIN.toFloat(), y - 8, (MARGIN + CONTENT_W).toFloat(), y - 8, hairline)
        val left = "GeoTag Camera ${BuildConfig.VERSION_NAME}  ·  generated ${fullTime(System.currentTimeMillis())}"
        text(c, left, MARGIN.toFloat(), y, CONTENT_W * 0.8f, muted, 1)
        val right = "$page / $total"
        c.drawText(right, (MARGIN + CONTENT_W) - muted.measureText(right), y + muted.textSize, muted)
    }

    /** Draws wrapped text and returns the y below it. */
    private fun text(c: Canvas, s: String, x: Float, y: Float, width: Number, paint: TextPaint, maxLines: Int): Float {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width.toFloat().toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return y + layout.height
    }

    /** Null when the file is gone or not an image; throws for the other reasons, so the page can say which. */
    private fun loadScaled(uri: Uri, maxW: Int, maxH: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun coords(p: PhotoEntity) = "%.6f, %.6f".format(Locale.US, p.latitude, p.longitude)
    private fun dateOnly(ms: Long) = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    private fun timeShort(ms: Long) = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
    private fun fullTime(ms: Long) = SimpleDateFormat("d MMM yyyy, HH:mm (z)", Locale.getDefault()).format(Date(ms))

    // ---- Type and paint ----

    private val poppins: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.poppins_regular) }.getOrNull() ?: Typeface.DEFAULT
    private val poppinsSemi: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.poppins_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val robotoMono: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.roboto_mono_regular) }.getOrNull() ?: Typeface.MONOSPACE

    private fun paint(face: Typeface, size: Float, color: Int, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = color; letterSpacing = spacing }

    private val title = paint(poppinsSemi, 26f, INK)
    private val heading = paint(poppinsSemi, 13f, INK)
    private val body = paint(poppins, 10.5f, INK)
    private val small = paint(poppins, 9f, INK)
    private val muted = paint(poppins, 8f, MUTED)
    private val label = paint(poppinsSemi, 7f, MUTED, 0.08f)
    private val mono = paint(robotoMono, 7.5f, MUTED)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val hairline = Paint().apply { color = Color.rgb(0xD8, 0xD5, 0xCF); style = Paint.Style.STROKE; strokeWidth = 0.6f }
    private val placeholder = Paint().apply { color = Color.rgb(0xEF, 0xED, 0xE9) }

    private companion object {
        // A4 in PDF points.
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40
        const val FOOTER = 24
        const val CONTENT_W = PAGE_W - MARGIN * 2
        const val PER_PAGE = 2
        const val MAP_W_PX = 1030
        const val MAP_H_PX = 620
        val INK = Color.rgb(0x16, 0x18, 0x1A)
        val MUTED = Color.rgb(0x6A, 0x6F, 0x75)
        val GREEN = Color.rgb(0x2F, 0xA8, 0x78)
        val AMBER = Color.rgb(0xE5, 0xA2, 0x4B)
        val RED = Color.rgb(0xE2, 0x49, 0x47)
        val GREY = Color.rgb(0x9A, 0xA0, 0xA6)
    }
}
