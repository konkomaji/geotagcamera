package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-tile trust dot. UNSIGNED/UNKNOWN draw no dot — only real signals are surfaced. */
enum class TileStatus { VERIFIED, TAMPERED, UNSIGNED, UNKNOWN }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val photoDao = AppDatabase.get(application).photoDao()

    val photos: StateFlow<List<PhotoEntity>> = photoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tileStatus = MutableStateFlow<Map<Long, TileStatus>>(emptyMap())
    val tileStatus: StateFlow<Map<Long, TileStatus>> = _tileStatus.asStateFlow()

    private val inFlight = mutableSetOf<Long>()

    init {
        // Background verification pass: as photos appear, verify each once and
        // cache the result. Surfaced on the tile, never silent — but a miss or
        // an unsigned photo shows no alarming dot.
        viewModelScope.launch {
            photos.collect { list -> verifyMissing(list) }
        }
    }

    private fun verifyMissing(list: List<PhotoEntity>) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            for (photo in list) {
                if (_tileStatus.value.containsKey(photo.id) || !inFlight.add(photo.id)) continue
                val status = when (PhotoVerification.verifyUri(context, Uri.parse(photo.filePath))) {
                    is VerificationOutcome.Untampered -> TileStatus.VERIFIED
                    is VerificationOutcome.Edited -> TileStatus.TAMPERED
                    is VerificationOutcome.NoProof -> TileStatus.UNSIGNED
                    is VerificationOutcome.Unreadable -> TileStatus.UNKNOWN
                }
                _tileStatus.value = _tileStatus.value + (photo.id to status)
            }
        }
    }
}
