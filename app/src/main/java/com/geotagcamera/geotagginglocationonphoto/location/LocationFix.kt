package com.geotagcamera.geotagginglocationonphoto.location

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val timestampEpochMs: Long,
    /** Monotonic fix time (Location.elapsedRealtimeNanos); immune to wall-clock changes. */
    val elapsedRealtimeNanos: Long? = null,
    /** From a mock-location app (developer options). Such a fix is never signed. */
    val isMock: Boolean = false
)
