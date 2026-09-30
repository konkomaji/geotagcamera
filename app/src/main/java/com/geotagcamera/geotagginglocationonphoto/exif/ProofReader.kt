package com.geotagcamera.geotagginglocationonphoto.exif

import androidx.exifinterface.media.ExifInterface
import com.geotagcamera.geotagginglocationonphoto.security.ObservedMeta
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pulls the integrity proof back out of a photo's bytes — EXIF UserComment
 * first, XMP mirror as fallback — and decodes it. Returns null when neither
 * carrier holds a valid GeoTag proof (a foreign or unsigned image), which the
 * verify flow surfaces as the neutral "no proof" outcome, not a failure.
 *
 * Reading from a byte array (not a live stream) so the exact same bytes feed
 * both the proof extraction here and the hash recomputation in
 * [com.geotagcamera.geotagginglocationonphoto.security.ProofVerifier].
 */
object ProofReader {
    fun read(bytes: ByteArray): SignedPayload? {
        val exifComment = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttribute(ExifInterface.TAG_USER_COMMENT)
        }.getOrNull()
        UserCommentCodec.decode(exifComment)?.let { return it }
        return UserCommentCodec.decode(XmpWriter.extract(bytes))
    }

    /** EXIF GPS position and GPS (UTC) time as actually present in the file, for checking against signed claims. */
    fun readObserved(bytes: ByteArray): ObservedMeta = runCatching {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        val latLong = exif.latLong
        val date = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)
        val time = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)
        val sec = if (date != null && time != null) {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse("$date $time")?.time?.div(1000)
        } else null
        ObservedMeta(latLong?.get(0), latLong?.get(1), sec)
    }.getOrDefault(ObservedMeta(null, null, null))
}
