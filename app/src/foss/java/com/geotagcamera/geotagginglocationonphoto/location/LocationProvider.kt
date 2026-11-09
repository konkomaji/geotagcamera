package com.geotagcamera.geotagginglocationonphoto.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Location for the stamp, "foss" flavor: Android's own LocationManager, no
 * Google Play services (what F-Droid and other FOSS stores require). Same API
 * as the "play" flavor.
 *
 * Uses the platform "fused" provider where the phone has one (Android 12+),
 * otherwise GPS and network together, keeping whichever fix is better.
 * Last-known fixes older than [MAX_LAST_KNOWN_AGE_MS] are never used.
 */
class LocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val manager = context.applicationContext.getSystemService(LocationManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context.applicationContext)

    private fun providers(): List<String> {
        val lm = manager ?: return emptyList()
        val enabled = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled) {
            return listOf(LocationManager.FUSED_PROVIDER)
        }
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in enabled }
    }

    /** Fixes every couple of seconds until the collector stops. Needs a location permission. */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 2_000L): Flow<LocationFix> = callbackFlow<Location> {
        val lm = manager
        if (lm == null) { close(); return@callbackFlow }
        val request = LocationRequestCompat.Builder(intervalMs)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        var best: Location? = null
        val listener = LocationListenerCompat { location ->
            if (isBetter(location, best)) {
                best = location
                trySend(location)
            }
        }
        val registered = providers().count { provider ->
            runCatching { LocationManagerCompat.requestLocationUpdates(lm, provider, request, listener, Looper.getMainLooper()) }.isSuccess
        }
        if (registered == 0) close()
        awaitClose { runCatching { LocationManagerCompat.removeUpdates(lm, listener) } }
    }.map { it.toLocationFix(appContext) }

    @SuppressLint("MissingPermission")
    suspend fun getFreshFix(freshFixTimeoutMs: Long = 8_000L): LocationFix? {
        val fresh = withTimeoutOrNull(freshFixTimeoutMs) { requestCurrentLocation() }
        if (fresh != null) return fresh
        return getLastKnownFix()?.takeIf { it.ageMs() <= MAX_LAST_KNOWN_AGE_MS }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): LocationFix? {
        val lm = manager ?: return null
        val provider = providers().firstOrNull() ?: return null
        val location = suspendCancellableCoroutine<Location?> { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
        return location?.toLocationFix(appContext)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownFix(): LocationFix? {
        val lm = manager ?: return null
        return providers()
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.toLocationFix(appContext)
    }

    /** Newer wins unless it is much less accurate than a fix that is still recent. */
    private fun isBetter(candidate: Location, current: Location?): Boolean {
        if (current == null) return true
        val newer = candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos
        if (newer > 10_000_000_000L) return true // current is more than 10 s older
        if (newer < 0) return false
        val accDelta = (if (candidate.hasAccuracy()) candidate.accuracy else Float.MAX_VALUE) -
            (if (current.hasAccuracy()) current.accuracy else Float.MAX_VALUE)
        return accDelta <= 0f || (accDelta < 50f && candidate.provider == current.provider)
    }


    companion object {
        /** Same limit the capture path applies to streamed fixes: nothing older is ever stamped. */
        const val MAX_LAST_KNOWN_AGE_MS = 30_000L
    }
}
