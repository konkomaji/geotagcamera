package com.geotagcamera.geotagginglocationonphoto.exif

import androidx.exifinterface.media.ExifInterface
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Embeds GPS + timing data into the JPEG's own EXIF tags, on top of the
 * visible stamp — so location survives even if a viewer strips or crops the
 * watermark, and GIS/photo tools that read EXIF pick it up automatically.
 */
object ExifWriter {

    fun write(file: File, fix: LocationFix, capturedAtEpochMs: Long, userComment: String? = null) {
        val exif = ExifInterface(file.absolutePath)

        exif.setLatLong(fix.latitude, fix.longitude)
        fix.altitudeMeters?.let { exif.setAltitude(it) }

        val dateTimeFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTimeFormat.format(capturedAtEpochMs))
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTimeFormat.format(capturedAtEpochMs))

        val gpsDateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val gpsTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFormat.format(capturedAtEpochMs))
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFormat.format(capturedAtEpochMs))

        // The pixels are already upright (the pipeline applies the camera's
        // rotation before stamping), so say so explicitly: 0 is not a valid value.
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        // Local time offset, so DateTimeOriginal isn't ambiguous across time zones.
        val offset = SimpleDateFormat("XXX", Locale.US).format(capturedAtEpochMs)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        // Horizontal accuracy, and the compass direction the camera faced (true north).
        fix.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }?.let {
            exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, "${(it * 100).toInt()}/100")
        }
        fix.bearingDegrees?.takeIf { it.isFinite() }?.let {
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.GPS_DIRECTION_TRUE)
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${(it * 100).toInt()}/100")
        }
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoTag Camera ${BuildConfig.VERSION_NAME}")

        userComment?.let { exif.setAttribute(ExifInterface.TAG_USER_COMMENT, it) }

        exif.saveAttributes()
    }
}
