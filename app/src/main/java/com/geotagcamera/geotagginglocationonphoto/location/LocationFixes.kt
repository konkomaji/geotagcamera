package com.geotagcamera.geotagginglocationonphoto.location

import android.content.Context
import android.location.Location
import androidx.core.location.LocationCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One conversion from a platform [Location] to a [LocationFix], shared by the
 * "foss" and "play" flavors.
 *
 * Altitude: GPS reports height above the WGS84 ellipsoid, but the stamp's ALT
 * chip and EXIF GPSAltitude mean height above sea level. The two differ by up
 * to about 100 m depending on where you are (roughly +47 m in Graz, -100 m in
 * southern India). [AltitudeConverterCompat] adds the sea-level altitude from
 * a small offline geoid map bundled with AndroidX, on every Android version.
 * If that fails, the fix carries no altitude rather than a wrong one.
 *
 * The geoid lookup reads a database, so this runs on [Dispatchers.IO].
 *
 * Bearing is left out on purpose: [Location.getBearing] is the direction of
 * travel, which is empty or wrong when the user stands still. The stamp's
 * bearing is the compass direction the camera faces ([CompassHeading]).
 */
suspend fun Location.toLocationFix(context: Context): LocationFix = withContext(Dispatchers.IO) {
    LocationFix(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = mslAltitudeMeters(context.applicationContext),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = null,
        timestampEpochMs = time,
        elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { it > 0L },
        isMock = LocationCompat.isMock(this@toLocationFix)
    )
}

private fun Location.mslAltitudeMeters(context: Context): Double? {
    if (!hasAltitude()) return null
    if (!LocationCompat.hasMslAltitude(this)) {
        runCatching { AltitudeConverterCompat.addMslAltitudeToLocation(context, this) }
    }
    return if (LocationCompat.hasMslAltitude(this)) LocationCompat.getMslAltitudeMeters(this) else null
}
