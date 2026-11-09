package com.geotagcamera.geotagginglocationonphoto.ui.maps

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.location.GeocoderRepository
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.location.LocationProvider
import com.geotagcamera.geotagginglocationonphoto.location.map.OfflineArea
import com.geotagcamera.geotagginglocationonphoto.location.map.OfflineMaps
import com.geotagcamera.geotagginglocationonphoto.util.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where "Save this area" will centre, once a fix is in. */
sealed interface HereState {
    data object Locating : HereState
    data object Unavailable : HereState
    data class Found(val fix: LocationFix, val placeName: String?) : HereState
}

class OfflineMapsViewModel(application: Application) : AndroidViewModel(application) {

    private val locationProvider = LocationProvider(application)
    private val geocoder = GeocoderRepository(application, AppDatabase.get(application).geoCacheDao())

    val areas: StateFlow<List<OfflineArea>> = OfflineMaps.areas

    private val _here = MutableStateFlow<HereState>(HereState.Locating)
    val here: StateFlow<HereState> = _here.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val network = Network.state(application)

    init {
        viewModelScope.launchSafely(tag = "OfflineMaps") { runCatching { OfflineMaps.load(application) } }
        locate()
    }

    fun locate() {
        _here.value = HereState.Locating
        viewModelScope.launchSafely(tag = "OfflineMaps") {
            // Throws without location permission; that's just "unavailable" here.
            val fix = runCatching { locationProvider.getFreshFix() }.getOrNull()
            if (fix == null) { _here.value = HereState.Unavailable; return@launchSafely }
            _here.value = HereState.Found(fix, null)
            val place = runCatching { geocoder.reverseGeocode(fix.latitude, fix.longitude)?.place }.getOrNull()
            _here.value = HereState.Found(fix, place)
        }
    }

    fun download(radiusKm: Int) {
        val found = _here.value as? HereState.Found ?: return
        val name = found.placeName ?: "%.4f, %.4f".format(java.util.Locale.US, found.fix.latitude, found.fix.longitude)
        viewModelScope.launchSafely(tag = "OfflineMaps") {
            val error = runCatching {
                OfflineMaps.download(getApplication(), name, found.fix.latitude, found.fix.longitude, radiusKm)
            }.getOrElse { it.message ?: "Couldn't start the download" }
            _message.value = error ?: "Downloading $name ($radiusKm km)"
        }
    }

    fun pause(id: Long) = viewModelScope.launchSafely(tag = "OfflineMaps") { runCatching { OfflineMaps.pause(id) } }
    fun resume(id: Long) = viewModelScope.launchSafely(tag = "OfflineMaps") { runCatching { OfflineMaps.resume(id) } }

    fun delete(id: Long) = viewModelScope.launchSafely(tag = "OfflineMaps") {
        val ok = runCatching { OfflineMaps.delete(id) }.getOrDefault(false)
        _message.value = if (ok) "Area deleted" else "Couldn't delete the area. Try again."
    }

    fun messageShown() { _message.value = null }
}
