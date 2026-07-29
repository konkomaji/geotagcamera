package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.Entity

/**
 * Last-known reverse-geocode result for a ~11m grid cell (lat/lng rounded to
 * 4 decimals). Read as a fallback the instant the live device Geocoder call
 * fails or times out, so stamping never blocks on network/GPS-fix latency —
 * this is what makes stamping offline-first instead of offline-broken.
 */
@Entity(tableName = "geo_cache", primaryKeys = ["latGridKey", "lngGridKey"])
data class GeoCacheEntity(
    val latGridKey: Long,
    val lngGridKey: Long,
    val address: String,
    val cachedAtEpochMs: Long
)

/** 4-decimal-place grid key (~11m cells) — coarse enough to reuse, fine enough not to blur addresses. */
fun gridKey(coordinate: Double): Long = Math.round(coordinate * 10_000.0)
