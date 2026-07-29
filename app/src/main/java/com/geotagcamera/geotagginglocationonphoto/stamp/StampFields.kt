package com.geotagcamera.geotagginglocationonphoto.stamp

/**
 * Which fields the field worker/student wants burned into the stamp.
 * Directly answers the "no way to select/deselect stamp fields" complaint
 * competitors get — see docs/research.md.
 */
data class StampFields(
    val showCoordinates: Boolean = true,
    val showAddress: Boolean = true,
    val showTimestamp: Boolean = true,
    val showAltitude: Boolean = false,
    val showAccuracy: Boolean = false,
    val showBearing: Boolean = false,
    val orgLabel: String = ""
) {
    val showOrgLabel: Boolean get() = orgLabel.isNotBlank()
}
