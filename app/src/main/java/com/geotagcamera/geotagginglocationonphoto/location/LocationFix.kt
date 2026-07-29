package com.geotagcamera.geotagginglocationonphoto.location

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val timestampEpochMs: Long
)
