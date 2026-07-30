package com.geotagcamera.geotagginglocationonphoto.location

import kotlin.math.floor

/**
 * Open Location Code ("Plus Code") encoder for the stamp's address line.
 * Pure math, no network, no API key, no dependency on any map SDK — this is
 * an open geocoding format, unrelated to the "no Google Maps SDK" rule,
 * which is about map tile rendering (see docs/design_brief.md).
 *
 * Re-derived from the Open Location Code spec (base-20 mixed-radix encoding
 * of a coordinate scaled to 0.000125-degree units, ~14m precision at the
 * equator, 10 code digits split 8+2 around a '+' separator) rather than
 * copied from Google's reference implementation. Verified, not just
 * derived: cross-checked against the `openlocationcode` reference Python
 * package for 7 coordinates including the equator, both poles, the
 * antimeridian, and a real field coordinate, all matched exactly, before
 * this port was written.
 */
object PlusCode {
    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private const val FINEST_RESOLUTION_DEGREES = 0.000125
    private const val UNITS_PER_DEGREE = 1.0 / FINEST_RESOLUTION_DEGREES // 8000.0
    private val PLACE_VALUES = longArrayOf(160000, 8000, 400, 20, 1)

    /** Encodes to the standard 10-digit global code, e.g. "7MJ9RXMC+Q4". */
    fun encode(latitude: Double, longitude: Double): String {
        var clippedLat = latitude.coerceIn(-90.0, 90.0)
        if (clippedLat >= 90.0) clippedLat = 90.0 - FINEST_RESOLUTION_DEGREES

        var lng = longitude % 360.0
        if (lng < -180.0) lng += 360.0
        if (lng >= 180.0) lng -= 360.0

        val latUnits = floor((clippedLat + 90.0) * UNITS_PER_DEGREE).toLong()
        val lngUnits = floor((lng + 180.0) * UNITS_PER_DEGREE).toLong()

        val chars = StringBuilder(PLACE_VALUES.size * 2)
        for (placeValue in PLACE_VALUES) {
            val latDigit = ((latUnits / placeValue) % 20).toInt()
            val lngDigit = ((lngUnits / placeValue) % 20).toInt()
            chars.append(CODE_ALPHABET[latDigit])
            chars.append(CODE_ALPHABET[lngDigit])
        }
        return chars.substring(0, SEPARATOR_POSITION) + SEPARATOR + chars.substring(SEPARATOR_POSITION)
    }
}
