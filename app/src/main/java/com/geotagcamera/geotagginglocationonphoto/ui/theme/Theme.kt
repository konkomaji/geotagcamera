package com.geotagcamera.geotagginglocationonphoto.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Tokens from docs/GeoTag Camera Design System.dc.html (section 01, Color).
 *
 * Two schemes, matching the doc exactly: Chrome is the camera layer and is
 * always dark, a viewfinder that flips to white in daylight mode would blow
 * out the photo it's framing. Surface (Gallery/Settings/Detail/Verify/About)
 * follows the system light/dark preference like a normal app. Material You
 * dynamic color is deferred (see docs/features.md roadmap); this ships a
 * fixed brand palette instead, entirely through named tokens, so flipping
 * that switch later is a token-source swap, not a rebuild.
 *
 * Every M3 role below maps to how the mockups actually use color, not a
 * default M3 assumption: filled buttons and "on" switches are ink, not an
 * accent hue. Accent (verified/live/pending/tamper) is reserved for status
 * chips and dots, never for general chrome.
 */
private val AccentVerified = Color(0xFF56CB98)
private val AccentLive = Color(0xFF6DB6FF)
private val AccentPending = Color(0xFFE5A24B)
private val AccentTamper = Color(0xFFE24947)

private val ChromeBase = Color(0xFF08090A)
private val ChromeRaised = Color(0xFF121417)
private val ChromePill = Color(0xFF1B1E22)
private val ChromeInk = Color(0xFFF5F7F8)
private val ChromeMuted = Color(0xFF9AA0A6)
private val ChromeOutline = Color(0xFF33383D)

private val SurfaceBg = Color(0xFFF7F6F4)
private val SurfaceCard = Color(0xFFFFFFFF)
private val SurfaceDim = Color(0xFFEFEDE9)
private val SurfaceInk = Color(0xFF16181A)
private val SurfaceMuted = Color(0xFF6A6F75)
private val SurfaceOutline = Color(0xFFD8D5CF)

private val SurfaceLightColors = lightColorScheme(
    primary = SurfaceInk,
    onPrimary = Color.White,
    primaryContainer = SurfaceCard,
    onPrimaryContainer = SurfaceInk,
    secondary = AccentLive,
    onSecondary = Color.White,
    tertiary = AccentPending,
    onTertiary = SurfaceInk,
    background = SurfaceBg,
    onBackground = SurfaceInk,
    surface = SurfaceBg,
    onSurface = SurfaceInk,
    surfaceVariant = SurfaceDim,
    onSurfaceVariant = SurfaceMuted,
    outline = SurfaceOutline,
    error = AccentTamper,
    onError = Color.White
)

private val SurfaceDarkColors = darkColorScheme(
    primary = ChromeInk,
    onPrimary = SurfaceInk,
    primaryContainer = ChromePill,
    onPrimaryContainer = ChromeInk,
    secondary = AccentLive,
    onSecondary = SurfaceInk,
    tertiary = AccentPending,
    onTertiary = SurfaceInk,
    background = ChromeRaised,
    onBackground = ChromeInk,
    surface = ChromeRaised,
    onSurface = ChromeInk,
    surfaceVariant = ChromePill,
    onSurfaceVariant = ChromeMuted,
    outline = ChromeOutline,
    error = AccentTamper,
    onError = Color.White
)

/** Camera layer: Capture, Launch, Onboarding, Signature pad. Always this scheme, never the system's. */
private val ChromeColors = darkColorScheme(
    primary = ChromeInk,
    onPrimary = ChromeBase,
    primaryContainer = ChromePill,
    onPrimaryContainer = ChromeInk,
    secondary = AccentLive,
    onSecondary = ChromeBase,
    tertiary = AccentPending,
    onTertiary = ChromeBase,
    background = ChromeBase,
    onBackground = ChromeInk,
    surface = ChromeRaised,
    onSurface = ChromeInk,
    surfaceVariant = ChromePill,
    onSurfaceVariant = ChromeMuted,
    outline = ChromeOutline,
    error = AccentTamper,
    onError = Color.White
)

/** Top-level theme for Gallery/Settings/Detail/Verify/About. Follows the system. */
@Composable
fun GeoTagCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SurfaceDarkColors else SurfaceLightColors
    // isAppearanceLightStatusBars=true means a light bar background, so dark icons; that's
    // exactly the light-theme case, hence !darkTheme.
    ApplyStatusBar(background = colorScheme.background, isAppearanceLight = !darkTheme)
    MaterialTheme(colorScheme = colorScheme, typography = GeoTagCameraTypography, content = content)
}

/** Nested override for the camera layer. Wrap Capture/Launch/Onboarding/Signature content in this. */
@Composable
fun GeoTagChromeTheme(content: @Composable () -> Unit) {
    // Chrome is always a dark bar background, so always light (white) icons.
    ApplyStatusBar(background = ChromeColors.background, isAppearanceLight = false)
    MaterialTheme(colorScheme = ChromeColors, typography = GeoTagCameraTypography, content = content)
}

@Composable
private fun ApplyStatusBar(background: Color, isAppearanceLight: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLight
    }
}
