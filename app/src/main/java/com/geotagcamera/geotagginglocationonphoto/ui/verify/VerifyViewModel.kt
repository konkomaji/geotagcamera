package com.geotagcamera.geotagginglocationonphoto.ui.verify

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VerifyUiState {
    data object Empty : VerifyUiState
    data object Verifying : VerifyUiState
    data class Done(val outcome: VerificationOutcome, val previewUri: String) : VerifyUiState
}

/**
 * Verifies any image the user points at — a share-target file, a gallery pick,
 * anything — with zero captures ever taken on this device. Reads the proof
 * and checks it portably via [PhotoVerification] (public key from the
 * file, no Keystore).
 */
class VerifyViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<VerifyUiState>(VerifyUiState.Empty)
    val state: StateFlow<VerifyUiState> = _state.asStateFlow()

    fun verify(uri: Uri) {
        _state.value = VerifyUiState.Verifying
        val context = getApplication<Application>()
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext VerificationOutcome.Unreadable
                PhotoVerification.verify(bytes)
            }
            _state.value = VerifyUiState.Done(outcome, uri.toString())
        }
    }
}
