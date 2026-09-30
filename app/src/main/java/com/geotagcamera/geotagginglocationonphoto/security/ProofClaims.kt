package com.geotagcamera.geotagginglocationonphoto.security

import java.util.Locale
import kotlin.math.abs

/**
 * The location/time facts a v2 proof commits to. The image hash alone is blind
 * to metadata (see JpegCanonical), so without this, EXIF GPS and timestamps
 * could be rewritten and the photo would still verify. These claims are folded
 * into the signed message and must agree with the EXIF a verifier observes.
 */
data class ProofClaims(val latitude: Double, val longitude: Double, val epochMs: Long) {

    /** Exact bytes that get signed: binds the image hash to the claims. */
    fun signedMessage(sha256Hex: String): ByteArray =
        String.format(Locale.US, "geotag-v2|h=%s|lat=%.6f|lon=%.6f|t=%d", sha256Hex, latitude, longitude, epochMs)
            .toByteArray(Charsets.UTF_8)
}

/** What a verifier actually finds in the file's EXIF; null = absent or unparseable. */
data class ObservedMeta(val latitude: Double?, val longitude: Double?, val gpsEpochSec: Long?) {

    fun matches(claims: ProofClaims): Boolean {
        val lat = latitude ?: return false
        val lon = longitude ?: return false
        val sec = gpsEpochSec ?: return false
        return abs(lat - claims.latitude) <= COORD_TOLERANCE_DEG &&
            abs(lon - claims.longitude) <= COORD_TOLERANCE_DEG &&
            abs(sec - Math.floorDiv(claims.epochMs, 1000L)) <= TIME_TOLERANCE_SEC
    }

    companion object {
        /** ~1 m; EXIF DMS rational rounding is far finer than this. */
        const val COORD_TOLERANCE_DEG = 1e-5
        const val TIME_TOLERANCE_SEC = 1L
    }
}
