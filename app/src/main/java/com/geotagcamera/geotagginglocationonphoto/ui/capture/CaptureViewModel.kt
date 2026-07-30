package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.exif.ExifWriter
import com.geotagcamera.geotagginglocationonphoto.location.LocationProvider
import com.geotagcamera.geotagginglocationonphoto.location.GeocoderRepository
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import com.geotagcamera.geotagginglocationonphoto.stamp.StampRenderer
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data object Processing : CaptureUiState
    data class Saved(val filePath: String) : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

/**
 * Owns the full shutter-to-saved-row pipeline: shoot, upright the pixels
 * (CameraX bakes orientation into EXIF, not the pixel data, and the stamp
 * render + resave below discards that tag), fetch a fresh GPS fix, reverse
 * geocode, burn the stamp, write EXIF GPS tags, sign for tamper-evidence,
 * then persist the row.
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val geocoderRepository = GeocoderRepository(application, AppDatabase.get(application).geoCacheDao())
    private val stampPreferences = StampPreferences(application)
    private val photoDao = AppDatabase.get(application).photoDao()

    val stampFields: StateFlow<StampFields> = stampPreferences.fields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampFields())

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun capture(imageCapture: ImageCapture) {
        if (_uiState.value == CaptureUiState.Processing) return
        val context = getApplication<Application>()

        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        _uiState.value = CaptureUiState.Processing
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processCapturedPhoto(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = CaptureUiState.Error("Capture failed: ${exception.message}")
                }
            }
        )
    }

    fun acknowledgeMessage() {
        _uiState.value = CaptureUiState.Idle
    }

    private fun processCapturedPhoto(file: File) {
        viewModelScope.launch {
            val capturedAtEpochMs = System.currentTimeMillis()
            val fix = locationProvider.getFreshFix()
            if (fix == null) {
                withContext(Dispatchers.IO) { file.delete() }
                _uiState.value = CaptureUiState.Error("Couldn't get a GPS fix. Move to open sky and try again.")
                return@launch
            }

            val geocode = runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
            val fields = stampFields.value

            val mediaUri = withContext(Dispatchers.IO) {
                val upright = loadUprightBitmap(file)
                val stamped = StampRenderer.stamp(upright, fix, geocode?.address, capturedAtEpochMs, fields)

                FileOutputStream(file).use { out -> stamped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                if (stamped !== upright) upright.recycle()
                stamped.recycle()

                ExifWriter.write(file, fix, capturedAtEpochMs)
                val integrity = PhotoIntegrity.sign(file)

                val uri = MediaStoreImageSaver.save(context, file, file.name)
                file.delete()
                uri?.let {
                    photoDao.insert(
                        PhotoEntity(
                            filePath = it.toString(),
                            capturedAtEpochMs = capturedAtEpochMs,
                            latitude = fix.latitude,
                            longitude = fix.longitude,
                            altitudeMeters = fix.altitudeMeters,
                            accuracyMeters = fix.accuracyMeters,
                            bearingDegrees = fix.bearingDegrees,
                            address = geocode?.address,
                            addressFromCache = geocode?.fromCache ?: false,
                            orgLabel = fields.orgLabel.ifBlank { null },
                            sha256Hash = integrity.sha256Hex,
                            signatureBase64 = integrity.signatureBase64,
                            signingKeyAlias = integrity.keyAlias,
                            fieldWorkerSignature = false
                        )
                    )
                }
                uri
            }

            if (mediaUri == null) {
                _uiState.value = CaptureUiState.Error("Couldn't save photo to gallery.")
                return@launch
            }

            _uiState.value = CaptureUiState.Saved(mediaUri.toString())
        }
    }

    private fun loadUprightBitmap(file: File): Bitmap {
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return bitmap

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
    }
}
