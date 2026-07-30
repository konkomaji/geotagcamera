package com.geotagcamera.geotagginglocationonphoto.stamp

/** Which of the three stamp layouts to render. See docs/GeoTag Camera Design System.dc.html section 04. */
enum class StampTemplate { CARD, BAR, MINIMAL }

/** One of nine positions the stamp card/bar/pill can sit at, matching the viewfinder's drag anchors. */
enum class StampAnchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, MID_LEFT, MID_CENTER, MID_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

/**
 * Which fields the field worker/student wants burned into the stamp, and how
 * the stamp itself is presented. Directly answers the "no way to select/
 * deselect stamp fields" complaint competitors get — see docs/research.md.
 *
 * Defaults match the design system's own component state exactly (its
 * `.dc.html` script block): map/country/address/coords/datetime/gmt/org/
 * logo/signature/brand default on, altitude/bearing/weather default off,
 * template CARD, position BOTTOM_LEFT. Note `showAccuracy` defaults `true`
 * here, a deliberate change from this field's old `false` default, to match
 * the locked design.
 *
 * `requireSignature` and `showSignatureField` are two different things:
 * `requireSignature` gates the *capture flow* (must a signature be collected
 * before saving at all), `showSignatureField` controls whether a captured
 * signature's "SIGNED" mark actually *displays* in the stamp. A signature
 * can be required but hidden from the stamp, or shown whenever present
 * without ever being required — keep these independent.
 */
data class StampFields(
    val template: StampTemplate = StampTemplate.CARD,
    val position: StampAnchor = StampAnchor.BOTTOM_LEFT,
    val showMap: Boolean = true,
    val showCountry: Boolean = true,
    val showAddress: Boolean = true,
    val showCoordinates: Boolean = true,
    val showTimestamp: Boolean = true,
    val showGmtOffset: Boolean = true,
    val showAltitude: Boolean = false,
    val showAccuracy: Boolean = true,
    val showBearing: Boolean = false,
    val showWeather: Boolean = false,
    val showOrgLabelToggle: Boolean = true,
    val orgLabel: String = "",
    val showOrgLogo: Boolean = true,
    val orgLogoUri: String? = null,
    val showSignatureField: Boolean = true,
    val requireSignature: Boolean = false,
    val showBrandMark: Boolean = true
) {
    /** Never show an empty line even if the toggle is on and the label is blank. */
    val showOrgLabel: Boolean get() = showOrgLabelToggle && orgLabel.isNotBlank()
}
