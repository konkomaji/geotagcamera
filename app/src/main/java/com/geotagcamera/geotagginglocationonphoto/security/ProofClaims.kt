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
            lonDelta(lon, claims.longitude) <= COORD_TOLERANCE_DEG &&
            sec == Math.floorDiv(claims.epochMs, 1000L)
    }

    fun hasAny(): Boolean = latitude != null || longitude != null || gpsEpochSec != null

    /** Shortest angular distance, so -180 and +180 count as the same meridian. */
    private fun lonDelta(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    companion object {
        /** ~0.1 m, matching the signed %.6f precision; ExifWriter's DMS rounding is ~1e-8. */
        const val COORD_TOLERANCE_DEG = 1e-6
    }
}
