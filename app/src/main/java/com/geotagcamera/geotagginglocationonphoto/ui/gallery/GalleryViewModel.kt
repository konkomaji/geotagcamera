package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VerifyStatus { UNCHECKED, CHECKING, VERIFIED, TAMPERED_OR_UNREADABLE }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val photoDao = AppDatabase.get(application).photoDao()

    val photos: StateFlow<List<PhotoEntity>> = photoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _verifyStatus = MutableStateFlow(VerifyStatus.UNCHECKED)
    val verifyStatus: StateFlow<VerifyStatus> = _verifyStatus.asStateFlow()

    fun verify(photo: PhotoEntity) {
        _verifyStatus.value = VerifyStatus.CHECKING
        viewModelScope.launch {
            val context = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    PhotoIntegrity.verify(context, Uri.parse(photo.filePath), photo.sha256Hash, photo.signatureBase64)
                }.getOrDefault(false)
            }
            _verifyStatus.value = if (ok) VerifyStatus.VERIFIED else VerifyStatus.TAMPERED_OR_UNREADABLE
        }
    }

    fun resetVerification() {
        _verifyStatus.value = VerifyStatus.UNCHECKED
    }
}
