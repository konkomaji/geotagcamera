package com.geotagcamera.geotagginglocationonphoto.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Fetches a fresh fix per capture rather than trusting `getLastLocation()`,
 * which is what produces the "stale address shown" complaint competitors get
 * — see docs/research.md. Falls back to the last-known fix only if a fresh
 * one can't be obtained within [freshFixTimeoutMs].
 */
class LocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun getFreshFix(freshFixTimeoutMs: Long = 8_000L): LocationFix? {
        val fresh = withTimeoutOrNull(freshFixTimeoutMs) { requestCurrentLocation() }
        if (fresh != null) return fresh
        return getLastKnownFix()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): LocationFix? = suspendCancellableCoroutine { cont ->
        val cancellationSource = CancellationTokenSource()
        cont.invokeOnCancellation { cancellationSource.cancel() }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        client.getCurrentLocation(request, cancellationSource.token)
            .addOnSuccessListener { location ->
                cont.resume(location?.toFix())
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownFix(): LocationFix? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { location -> cont.resume(location?.toFix()) }
            .addOnFailureListener { cont.resume(null) }
    }

    private fun android.location.Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        timestampEpochMs = time
    )
}
