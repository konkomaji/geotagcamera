package com.geotagcamera.geotagginglocationonphoto.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.geotagcamera.geotagginglocationonphoto.data.GeoCacheDao
import com.geotagcamera.geotagginglocationonphoto.data.GeoCacheEntity
import com.geotagcamera.geotagginglocationonphoto.data.gridKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The stamp's anchor line, full address line, and country chip need more
 * than the one joined string this repository used to produce. A live
 * geocode has all of it; a cache hit (the old `GeoCacheEntity` schema is
 * unchanged, still just one address string) degrades gracefully to
 * `place == addressLine` and `countryName/countryCode == null` — the stamp
 * layer already has to reflow around any field being absent, so this is no
 * special case, just another field that isn't always there.
 */
data class AddressParts(
    val place: String,
    val addressLine: String,
    val countryName: String?,
    val countryCode: String?,
    val fromCache: Boolean
)

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

    suspend fun reverseGeocode(latitude: Double, longitude: Double): AddressParts? {
        val live = tryLiveGeocode(latitude, longitude)
        if (live != null) {
            geoCacheDao.upsert(
                GeoCacheEntity(
                    latGridKey = gridKey(latitude),
                    lngGridKey = gridKey(longitude),
                    address = live.addressLine,
                    cachedAtEpochMs = System.currentTimeMillis()
                )
            )
            return live
        }

        val cached = geoCacheDao.getNearby(gridKey(latitude), gridKey(longitude), radius = 25)
        return cached?.let {
            AddressParts(
                place = it.address,
                addressLine = it.address,
                countryName = null,
                countryCode = null,
                fromCache = true
            )
        }
    }

    private suspend fun tryLiveGeocode(latitude: Double, longitude: Double): AddressParts? {
        if (!Geocoder.isPresent()) return null
        return try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                fetchAddressesAsync(latitude, longitude)
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) { geocoder.getFromLocation(latitude, longitude, 1) }
            }
            addresses?.firstOrNull()?.let(::toAddressParts)
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun fetchAddressesAsync(latitude: Double, longitude: Double): List<Address>? =
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                cont.resume(addresses)
            }
        }

    private fun toAddressParts(address: Address): AddressParts {
        val place = listOfNotNull(
            address.locality ?: address.subLocality,
            address.adminArea
        ).joinToString(", ").ifBlank { address.getAddressLine(0).orEmpty() }

        val lineParts = listOfNotNull(
            address.subLocality,
            address.locality,
            address.adminArea,
            address.postalCode
        )
        val addressLine = if (lineParts.isNotEmpty()) lineParts.joinToString(", ") else address.getAddressLine(0).orEmpty()

        return AddressParts(
            place = place,
            addressLine = addressLine,
            countryName = address.countryName,
            countryCode = address.countryCode,
            fromCache = false
        )
    }
}
