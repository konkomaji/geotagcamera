package com.geotagcamera.geotagginglocationonphoto.ui.detail

import android.app.Application
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * State for one photo's detail. Metadata is read from the file's **live EXIF**
 * at view time, not from PhotoEntity's cached columns — so if a photo was
 * edited after capture, Detail shows the current bytes (and the verification
 * outcome flips to Edited). That's the concrete reason Detail needs no DB
 * schema change: the file, plus its embedded proof, is the source of truth.
 */
data class PhotoDetailState(
    val loading: Boolean = true,
    val photo: PhotoEntity? = null,
    val outcome: VerificationOutcome? = null,
    val exifDateTime: String? = null,
    val exifLat: Double? = null,
    val exifLng: Double? = null
)

class PhotoDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val photoDao = AppDatabase.get(application).photoDao()
    private val _state = MutableStateFlow(PhotoDetailState())
    val state: StateFlow<PhotoDetailState> = _state.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            val photo = photoDao.getById(id)
            if (photo == null) {
                _state.value = PhotoDetailState(loading = false)
                return@launch
            }
            val context = getApplication<Application>()
            val read = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(photo.filePath))?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext null

                val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
                val latLng = exif?.latLong
                val dateTime = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
                DetailRead(
                    outcome = PhotoVerification.verify(bytes),
                    dateTime = dateTime,
                    lat = latLng?.getOrNull(0),
                    lng = latLng?.getOrNull(1)
                )
            }
            _state.value = PhotoDetailState(
                loading = false,
                photo = photo,
                outcome = read?.outcome ?: VerificationOutcome.Unreadable,
                exifDateTime = read?.dateTime,
                exifLat = read?.lat,
                exifLng = read?.lng
            )
        }
    }

    private data class DetailRead(
        val outcome: VerificationOutcome,
        val dateTime: String?,
        val lat: Double?,
        val lng: Double?
    )
}
