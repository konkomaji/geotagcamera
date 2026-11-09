package com.geotagcamera.geotagginglocationonphoto.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Screenshots and screen recordings for a report, made safe to send.
 *
 * - Images are decoded and re-encoded as a fresh JPEG, at most [MAX_IMAGE_EDGE]
 *   px on the long edge. Re-encoding drops all EXIF (GPS included), XMP, and
 *   anything else riding along in the original file.
 * - Videos can't be cleaned on the phone without a muxer, so they are only
 *   accepted as MP4/WebM up to [MAX_VIDEO_BYTES], and the screen warns that
 *   a camera video (not a screen recording) may carry its location.
 *
 * Files come from the system photo picker, so the app needs no storage
 * permission and only sees what the user picked.
 */
object ReportAttachments {

    const val MAX_IMAGES = 3
    const val MAX_VIDEOS = 1
    const val MAX_IMAGE_EDGE = 1600
    const val MAX_VIDEO_BYTES = 20L * 1024 * 1024
    private val VIDEO_TYPES = setOf("video/mp4", "video/webm")

    data class Prepared(val file: File, val mime: String, val label: String)

    sealed interface Outcome {
        data class Ok(val attachment: Prepared) : Outcome
        data class Refused(val reason: String) : Outcome
    }

    suspend fun prepare(context: Context, uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val dir = File(context.cacheDir, "report-attachments").apply { mkdirs() }
        try {
            when {
                mime.startsWith("image/") -> {
                    val bitmap = decodeScaled(context, uri) ?: return@withContext Outcome.Refused("That image couldn't be read.")
                    val out = File(dir, "screenshot-${System.nanoTime()}.jpg")
                    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bitmap.recycle()
                    Outcome.Ok(Prepared(out, "image/jpeg", "Screenshot ${out.length() / 1024} KB"))
                }
                mime in VIDEO_TYPES -> {
                    val size = sizeOf(context, uri)
                    if (size == null || size > MAX_VIDEO_BYTES) {
                        return@withContext Outcome.Refused("Videos can be up to ${MAX_VIDEO_BYTES / 1024 / 1024} MB. Trim it or record a shorter clip.")
                    }
                    val ext = if (mime == "video/webm") "webm" else "mp4"
                    val out = File(dir, "recording-${System.nanoTime()}.$ext")
                    val copied = resolver.openInputStream(uri)?.use { input -> out.outputStream().use { copyCapped(input, it) } }
                        ?: return@withContext Outcome.Refused("That video couldn't be read.")
                    if (!copied) { out.delete(); return@withContext Outcome.Refused("That video is too large.") }
                    Outcome.Ok(Prepared(out, mime, "Recording ${out.length() / 1024 / 1024} MB"))
                }
                else -> Outcome.Refused("Only screenshots (images) and MP4 or WebM videos can be attached.")
            }
        } catch (e: OutOfMemoryError) {
            Outcome.Refused("That image is too large to attach.")
        } catch (e: Exception) {
            Outcome.Refused("That file couldn't be attached.")
        }
    }

    /** Removes everything prepared for reports (after sending, or leaving the screen). */
    fun clear(context: Context) {
        File(context.cacheDir, "report-attachments").deleteRecursively()
    }

    /** False (and stops) once more than [MAX_VIDEO_BYTES] arrive, whatever size the provider claimed. */
    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream): Boolean {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return true
            total += n
            if (total > MAX_VIDEO_BYTES) return false
            output.write(buf, 0, n)
        }
    }

    private fun sizeOf(context: Context, uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val edge = max(decoded.width, decoded.height)
        if (edge <= MAX_IMAGE_EDGE) return decoded
        val scale = MAX_IMAGE_EDGE.toFloat() / edge
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            .also { if (it !== decoded) decoded.recycle() }
    }
}
