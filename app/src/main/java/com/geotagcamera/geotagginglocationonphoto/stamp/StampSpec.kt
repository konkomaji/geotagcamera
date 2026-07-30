package com.geotagcamera.geotagginglocationonphoto.stamp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.location.PlusCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** One meta chip, e.g. "ALT 34 m" or "31°C HAZE" — see docs/GeoTag Camera Design System.dc.html section 04. */
data class StampChip(val text: String)

/**
 * Everything needed to draw one stamp, already resolved: which of the 14
 * fields are present, their formatted text, and the chosen template/anchor.
 * Nothing here is positional — [StampPainter] lays every field out fresh
 * from whichever of these are non-null/non-empty, so turning any field off
 * upstream (in [StampFields]) just shortens what this spec carries and the
 * drawn layout reflows around it. This is what makes "what you frame is
 * what burns in" true by construction: the exact same spec, built from the
 * exact same [buildStampSpec], feeds both the live viewfinder overlay and
 * the final bitmap burn-in.
 */
data class StampSpec(
    val template: StampTemplate,
    val anchor: StampAnchor,
    val placeName: String?,
    val countryCode: String?,
    val addressLine: String?,
    val coordinatesText: String?,
    val dateTimeText: String?,
    val gmtOffsetText: String?,
    val chips: List<StampChip>,
    val orgLabel: String?,
    val orgLogo: ImageBitmap?,
    val showSignedMark: Boolean,
    val showBrandMark: Boolean,
    val mapTile: ImageBitmap?
) {
    val hasFooterRow: Boolean get() = orgLabel != null || orgLogo != null || showSignedMark || showBrandMark
}

/**
 * Pure resolution step: fix + address + capture time + [StampFields] + the
 * already-loaded org logo/map tile bitmaps (both may be null — a missing
 * logo or a map-tile cache miss both just mean that field doesn't render,
 * never a blocked capture) become one fully-formed [StampSpec].
 */
fun buildStampSpec(
    fix: LocationFix,
    addressParts: AddressParts?,
    capturedAtEpochMs: Long,
    fields: StampFields,
    mapTile: Bitmap?,
    orgLogo: Bitmap?,
    hasSignature: Boolean
): StampSpec {
    val countryCode = if (fields.showCountry) {
        addressParts?.countryCode?.let { code ->
            runCatching { Locale("", code).isO3Country.uppercase(Locale.US) }.getOrNull()
                ?: code.uppercase(Locale.US)
        }
    } else null

    val addressLine = if (fields.showAddress) {
        val base = addressParts?.addressLine
        val plusCode = PlusCode.encode(fix.latitude, fix.longitude)
        when {
            base.isNullOrBlank() -> plusCode
            else -> "$plusCode, $base"
        }
    } else null

    val coordinatesText = if (fields.showCoordinates) {
        "Lat %.6f°  Long %.6f°".format(Locale.US, fix.latitude, fix.longitude)
    } else null

    val dateTimeText = if (fields.showTimestamp) {
        SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(capturedAtEpochMs))
    } else null

    val gmtOffsetText = if (fields.showTimestamp && fields.showGmtOffset) formatGmtOffset(capturedAtEpochMs) else null

    val chips = buildList {
        if (fields.showAltitude) fix.altitudeMeters?.let { add(StampChip("ALT ${it.roundToInt()} m")) }
        if (fields.showAccuracy) fix.accuracyMeters?.let { add(StampChip("±${it.roundToInt()} m")) }
        if (fields.showBearing) fix.bearingDegrees?.let { add(StampChip("${it.roundToInt()}°")) }
        // Weather chip is appended by the caller once Phase 3's WeatherRepository exists;
        // fields.showWeather is read there, not here, since this function has no network access.
    }

    val placeName = if (addressParts != null) addressParts.place else null

    return StampSpec(
        template = fields.template,
        anchor = fields.position,
        placeName = placeName,
        countryCode = countryCode,
        addressLine = addressLine,
        coordinatesText = coordinatesText,
        dateTimeText = dateTimeText,
        gmtOffsetText = gmtOffsetText,
        chips = chips,
        orgLabel = if (fields.showOrgLabel) fields.orgLabel else null,
        orgLogo = if (fields.showOrgLogo) orgLogo?.asImageBitmap() else null,
        showSignedMark = fields.showSignatureField && hasSignature,
        showBrandMark = fields.showBrandMark,
        mapTile = if (fields.showMap) mapTile?.asImageBitmap() else null
    )
}

private fun formatGmtOffset(epochMs: Long): String {
    val offsetMs = TimeZone.getDefault().getOffset(epochMs)
    val sign = if (offsetMs >= 0) "+" else "-"
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(kotlin.math.abs(offsetMs.toLong()))
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "GMT %s%02d:%02d".format(Locale.US, sign, hours, minutes)
}
