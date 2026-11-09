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
    val mapTile: ImageBitmap?,
    /** The field worker's drawn signature, shown in the Bar's right column. */
    val signature: ImageBitmap? = null,
    /** Capture note and/or the fixed custom text, as one line. */
    val noteLine: String? = null
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
    hasSignature: Boolean,
    weatherChipText: String? = null,
    signature: Bitmap? = null,
    note: String? = null
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
        // The gap after "Lat" and "Long" is a non-breaking space (U+00A0): if the
        // line wraps, it wraps between the two pairs, never inside one.
        "Lat %.6f°  Long %.6f°".format(Locale.US, fix.latitude, fix.longitude)
    } else null

    val dateTimeText = if (fields.showTimestamp) {
        SimpleDateFormat(stampDateTimePattern(Locale.getDefault()), Locale.getDefault()).format(Date(capturedAtEpochMs))
    } else null

    val gmtOffsetText = if (fields.showTimestamp && fields.showGmtOffset) formatGmtOffset(capturedAtEpochMs) else null

    val chips = buildList {
        if (fields.showAltitude) fix.altitudeMeters?.takeIf { it.isFinite() }?.let { add(StampChip("ALT ${it.roundToInt()} m")) }
        if (fields.showAccuracy) fix.accuracyMeters?.takeIf { it.isFinite() }?.let { add(StampChip("±${it.roundToInt()} m")) }
        if (fields.showBearing) fix.bearingDegrees?.takeIf { it.isFinite() }?.let { add(StampChip("${it.roundToInt()}°")) }
        // Weather text is fetched by the caller (WeatherRepository needs network,
        // which this pure function has none of) and passed in already-formatted;
        // gated here so the live overlay and the burn-in stay identical.
        if (fields.showWeather) weatherChipText?.takeIf { it.isNotBlank() }?.let { add(StampChip(it)) }
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
        showSignedMark = fields.showSignatureField && (hasSignature || signature != null),
        showBrandMark = fields.showBrandMark,
        mapTile = if (fields.showMap) mapTile?.asImageBitmap() else null,
        signature = signature?.asImageBitmap(),
        noteLine = listOfNotNull(
            note?.trim()?.takeIf { it.isNotEmpty() },
            fields.customText.trim().takeIf { fields.showCustomText && it.isNotEmpty() }
        ).joinToString("  ·  ").ifEmpty { null }
    )
}

/**
 * Weekday plus the locale's own short date and time, with a four-digit year:
 * "Wednesday, 10/14/2026, 11:36 PM" in the US, "Mittwoch, 14.10.2026, 23:36"
 * in Germany, "Wednesday, 14/10/2026, 11:36 pm" in India. A fixed
 * dd/MM/yyyy hh:mm a pattern read wrong outside English locales (F-Droid
 * review of 1.4.1).
 */
internal fun stampDateTimePattern(locale: Locale): String {
    val short = runCatching {
        java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            java.time.format.FormatStyle.SHORT, java.time.format.FormatStyle.SHORT,
            java.time.chrono.IsoChronology.INSTANCE, locale
        )
    }.getOrNull() ?: return "EEEE, dd/MM/yyyy HH:mm"
    // Short date styles often use a two-digit year; a field record needs all four.
    val fullYear = Regex("(?<![y'])y{1,2}(?![y'])").replace(short, "yyyy")
    return "EEEE, $fullYear"
}

private fun formatGmtOffset(epochMs: Long): String {
    val offsetMs = TimeZone.getDefault().getOffset(epochMs)
    val sign = if (offsetMs >= 0) "+" else "-"
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(kotlin.math.abs(offsetMs.toLong()))
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "GMT %s%02d:%02d".format(Locale.US, sign, hours, minutes)
}
