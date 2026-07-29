package com.geotagcamera.geotagginglocationonphoto.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.geotagcamera.geotagginglocationonphoto.data.GeoCacheDao
import com.geotagcamera.geotagginglocationonphoto.data.GeoCacheEntity
import com.geotagcamera.geotagginglocationonphoto.data.gridKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeocodeResult(val address: String, val fromCache: Boolean)

/**
 * Reverse-geocodes using the on-device Android [Geocoder] — no network key,
 * no third-party server, works offline on OEM builds that ship a local
 * geocode provider. When the device Geocoder is unavailable or returns
 * nothing (common in rural/field-work dead zones — see docs/research.md),
 * falls back to the nearest cached result instead of leaving the stamp blank.
 */
class GeocoderRepository(
    private val context: Context,
    private val geoCacheDao: GeoCacheDao
) {
    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult? {
        val live = tryLiveGeocode(latitude, longitude)
        if (live != null) {
            geoCacheDao.upsert(
                GeoCacheEntity(
                    latGridKey = gridKey(latitude),
                    lngGridKey = gridKey(longitude),
                    address = live,
                    cachedAtEpochMs = System.currentTimeMillis()
                )
            )
            return GeocodeResult(live, fromCache = false)
        }

        val cached = geoCacheDao.getNearby(gridKey(latitude), gridKey(longitude), radius = 25)
        return cached?.let { GeocodeResult(it.address, fromCache = true) }
    }

    private suspend fun tryLiveGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                fetchAddressesAsync(latitude, longitude)
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) { geocoder.getFromLocation(latitude, longitude, 1) }
            }
            addresses?.firstOrNull()?.let(::formatAddress)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchAddressesAsync(latitude: Double, longitude: Double): List<Address>? =
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                cont.resume(addresses)
            }
        }

    private fun formatAddress(address: Address): String {
        val parts = listOfNotNull(
            address.subLocality,
            address.locality,
            address.adminArea,
            address.postalCode
        )
        return if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0).orEmpty()
    }
}
