package com.geotagcamera.geotagginglocationonphoto.ui.detail

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import android.app.Application
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
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
        viewModelScope.launchSafely(tag = "PhotoDetail") {
            val photo = photoDao.getById(id)
            if (photo == null) {
                _state.value = PhotoDetailState(loading = false)
                return@launchSafely
            }
            val context = getApplication<Application>()
            val read = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    com.geotagcamera.geotagginglocationonphoto.security.OriginalBytes.read(context, Uri.parse(photo.filePath))
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

    private val _deleteConsent = MutableStateFlow<android.content.IntentSender?>(null)
    /** A system "allow deleting this photo" prompt the screen must launch. */
    val deleteConsent: StateFlow<android.content.IntentSender?> = _deleteConsent.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    /** True once the photo is gone; the screen navigates back. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun delete() {
        val photo = _state.value.photo ?: return
        viewModelScope.launchSafely(tag = "PhotoDetail") {
            val outcome = withContext(Dispatchers.IO) {
                MediaStoreImageSaver.delete(getApplication(), listOf(Uri.parse(photo.filePath)))
            }
            when {
                outcome.deleted.isNotEmpty() -> {
                    photoDao.delete(photo.id)
                    _deleted.value = true
                }
                outcome.consent != null -> _deleteConsent.value = outcome.consent
                // Couldn't delete the file: keep the row so the photo isn't orphaned.
                else -> Unit
            }
        }
    }

    fun onDeleteConsentResult(granted: Boolean) {
        _deleteConsent.value = null
        val photo = _state.value.photo ?: return
        if (granted) viewModelScope.launchSafely(tag = "PhotoDetail") {
            photoDao.delete(photo.id)
            _deleted.value = true
        }
    }

    private data class DetailRead(
        val outcome: VerificationOutcome,
        val dateTime: String?,
        val lat: Double?,
        val lng: Double?
    )
}
