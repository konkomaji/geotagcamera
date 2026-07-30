package com.geotagcamera.geotagginglocationonphoto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.geotagcamera.geotagginglocationonphoto.R

/**
 * Type from docs/GeoTag Camera Design System.dc.html (section 01, Type):
 * "Poppins for language, Roboto Mono for anything a person might read back
 * into a form: coordinates, hashes, accuracy, bearing. Numbers get tabular
 * figures so a stamp doesn't jitter as digits change."
 *
 * Bundled as static .ttf resources (res/font/), not the Google Fonts
 * downloadable-font provider, so there's no first-run network fetch for
 * something this core, matching the app's offline-first rule everywhere
 * else.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

val RobotoMono = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
    Font(R.font.roboto_mono_bold, FontWeight.Bold)
)

val GeoTagCameraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 36.8.sp,
        letterSpacing = (-0.02).em(32)
    ),
    headlineSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.015).em(24)
    ),
    titleLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 26.4.sp,
        letterSpacing = (-0.01).em(22)
    ),
    titleMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.75.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.5.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.03.em(12),
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.3.sp,
        letterSpacing = 0.12.em(11)
    )
)

/** The stamp's one 700-weight anchor line, "the one thing readable from arm's length." */
val StampAnchorStyle = TextStyle(
    fontFamily = Poppins,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    lineHeight = 21.25.sp,
    letterSpacing = (-0.01).em(17)
)

/** Coordinates, hashes, accuracy, bearing: anything transcribed into a report. Tabular figures. */
val MonoDataStyle = TextStyle(
    fontFamily = RobotoMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.2.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum"
)

/** letter-spacing in Compose is an absolute Sp/Em value, not a CSS-style em multiplied by font size;
 * this converts a design-system "em" figure (relative to its own font size) to the Sp Compose needs. */
private fun Double.em(fontSizeSp: Int): androidx.compose.ui.unit.TextUnit = (this * fontSizeSp).sp
