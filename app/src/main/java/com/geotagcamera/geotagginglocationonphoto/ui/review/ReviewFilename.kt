package com.geotagcamera.geotagginglocationonphoto.ui.review

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the human-quotable capture filename shown on the review screen and
 * used as the MediaStore display name, e.g. `IMG_20260731_0140_Champadanga.jpg`.
 * Address-derived so field staff can read it aloud on site (design section 04).
 * Pure; no Android dependency.
 */
object ReviewFilename {
    fun generate(place: String?, epochMs: Long): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(epochMs))
        val slug = place
            ?.substringBefore(",")
            ?.trim()
            ?.filter { it.isLetterOrDigit() }
            ?.takeIf { it.isNotBlank() }
        return if (slug != null) "IMG_${ts}_$slug.jpg" else "IMG_$ts.jpg"
    }

    /** "3.2 MB" / "812 KB" — for the size caption under the filename. */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(Locale.US, bytes / 1_000_000.0)
        bytes >= 1_000 -> "%d KB".format(Locale.US, bytes / 1_000)
        else -> "$bytes B"
    }
}
