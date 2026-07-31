package com.geotagcamera.geotagginglocationonphoto.exif

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

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
}
