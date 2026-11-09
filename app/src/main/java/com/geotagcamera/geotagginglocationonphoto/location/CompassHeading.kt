package com.geotagcamera.geotagginglocationonphoto.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The compass direction the back camera faces, for the stamp's bearing chip
 * and EXIF GPSImgDirection.
 *
 * GPS bearing ([android.location.Location.getBearing]) is the direction of
 * travel: it needs the user to be walking, lags by seconds, and is often
 * wrong for a photo taken while standing still (GitHub issue #11). This reads
 * the phone's orientation sensors instead, like a compass app does.
 *
 * The camera looks out of the back of the phone, along the device's -Z axis.
 * That vector in world coordinates gives the heading whatever way the phone
 * is held (portrait, landscape, tilted). When the phone lies nearly flat the
 * camera points at the ground and that heading means nothing, so the top
 * edge of the phone is used instead, as a map app does.
 *
 * Values are magnetic headings in degrees, 0..360. [toTrueNorth] applies the
 * local declination once a fix is known.
 */
class CompassHeading(context: Context) {
    private val sensors = context.applicationContext.getSystemService(SensorManager::class.java)

    /** True when the phone has a sensor this class can use. */
    val isAvailable: Boolean get() = pickSensor() != null

    // Rotation vector fuses gyro, accelerometer and magnetometer. The
    // geomagnetic one has no gyro (cheaper phones) but still points north.
    private fun pickSensor(): Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    /** Magnetic headings, smoothed, emitted when they change by a degree or more. */
    fun headings(): Flow<Float> {
        val sm = sensors ?: return emptyFlow()
        val sensor = pickSensor() ?: return emptyFlow()
        return callbackFlow {
            val r = FloatArray(9)
            var sinAvg = 0.0
            var cosAvg = 0.0
            var started = false
            var lastSent = Float.NaN
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(r, event.values)
                    val raw = cameraAzimuthDegrees(r) ?: return
                    val rad = Math.toRadians(raw.toDouble())
                    // Average on the circle, so 359° and 1° give 0°, not 180°.
                    if (!started) { sinAvg = sin(rad); cosAvg = cos(rad); started = true }
                    else { sinAvg += SMOOTHING * (sin(rad) - sinAvg); cosAvg += SMOOTHING * (cos(rad) - cosAvg) }
                    val smooth = normalize(Math.toDegrees(atan2(sinAvg, cosAvg)).toFloat())
                    if (lastSent.isNaN() || angleDiff(smooth, lastSent) >= 1f) {
                        lastSent = smooth
                        trySend(smooth)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) close()
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    companion object {
        private const val SMOOTHING = 0.2

        /**
         * Heading of the back camera from a rotation matrix (device to world;
         * world X = east, Y = north, Z = up). Null if the matrix is degenerate.
         */
        internal fun cameraAzimuthDegrees(r: FloatArray): Float? {
            // Device -Z (out of the back camera) in world coordinates: column 2, negated.
            var east = -r[2]
            var north = -r[5]
            if (hypot(east, north) < FLAT_LIMIT) {
                // Phone almost flat: use where the top edge points (device +Y, column 1).
                east = r[1]
                north = r[4]
            }
            if (hypot(east, north) < 1e-3f) return null
            return normalize(Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat())
        }

        /** Below this horizontal share the camera faces the ground (about 25° from straight down). */
        private const val FLAT_LIMIT = 0.42f

        /** Magnetic to true north at [fix]. */
        fun toTrueNorth(magneticDegrees: Float, fix: LocationFix): Float {
            val field = GeomagneticField(
                fix.latitude.toFloat(),
                fix.longitude.toFloat(),
                (fix.altitudeMeters ?: 0.0).toFloat(),
                fix.timestampEpochMs
            )
            return normalize(magneticDegrees + field.declination)
        }

        internal fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

        internal fun angleDiff(a: Float, b: Float): Float {
            val d = abs(a - b) % 360f
            return if (d > 180f) 360f - d else d
        }
    }
}
