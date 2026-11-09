package com.geotagcamera.geotagginglocationonphoto.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotation matrices are row-major, device to world (X east, Y north, Z up),
 * the layout SensorManager.getRotationMatrixFromVector produces.
 */
class CompassHeadingTest {
    /** Phone upright in portrait, back camera facing [heading] degrees. */
    private fun upright(heading: Double): FloatArray {
        val h = Math.toRadians(heading)
        // Device X (right edge) points heading+90, Y (top) points up, Z (screen) points at the user.
        val x = floatArrayOf(cos(h).toFloat(), (-sin(h)).toFloat(), 0f)
        val y = floatArrayOf(0f, 0f, 1f)
        val z = floatArrayOf((-sin(h)).toFloat(), (-cos(h)).toFloat(), 0f)
        return floatArrayOf(x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2])
    }

    /** Phone flat on a table, screen up, top edge pointing [heading] degrees. */
    private fun flat(heading: Double): FloatArray {
        val h = Math.toRadians(heading)
        val x = floatArrayOf(cos(h).toFloat(), (-sin(h)).toFloat(), 0f)
        val y = floatArrayOf(sin(h).toFloat(), cos(h).toFloat(), 0f)
        val z = floatArrayOf(0f, 0f, 1f)
        return floatArrayOf(x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2])
    }

    @Test fun uprightPhoneReportsWhereTheCameraFaces() {
        for (h in listOf(0.0, 45.0, 90.0, 180.0, 270.0, 359.0)) {
            val got = CompassHeading.cameraAzimuthDegrees(upright(h))
            assertNotNull(got)
            assertEquals("heading $h", 0f, CompassHeading.angleDiff(got!!, h.toFloat()), 0.01f)
        }
    }

    @Test fun flatPhoneFallsBackToTheTopEdge() {
        for (h in listOf(0.0, 90.0, 225.0)) {
            val got = CompassHeading.cameraAzimuthDegrees(flat(h))!!
            assertEquals("heading $h", 0f, CompassHeading.angleDiff(got, h.toFloat()), 0.01f)
        }
    }

    @Test fun angleDiffWrapsAroundNorth() {
        assertEquals(2f, CompassHeading.angleDiff(359f, 1f), 0.001f)
        assertEquals(0f, CompassHeading.normalize(360f), 0.001f)
        assertEquals(350f, CompassHeading.normalize(-10f), 0.001f)
    }
}
