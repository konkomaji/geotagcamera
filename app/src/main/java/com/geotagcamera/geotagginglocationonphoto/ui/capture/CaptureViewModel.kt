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
import com.geotagcamera.geotagginglocationonphoto.exif.UserCommentCodec
import com.geotagcamera.geotagginglocationonphoto.exif.XmpWriter
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.location.LocationProvider
import com.geotagcamera.geotagginglocationonphoto.location.GeocoderRepository
import com.geotagcamera.geotagginglocationonphoto.location.TileMapRepository
import com.geotagcamera.geotagginglocationonphoto.location.WeatherReading
import com.geotagcamera.geotagginglocationonphoto.location.WeatherRepository
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import com.geotagcamera.geotagginglocationonphoto.signature.SignatureOverlay
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import com.geotagcamera.geotagginglocationonphoto.stamp.StampRenderer
import com.geotagcamera.geotagginglocationonphoto.stamp.StampSpec
import com.geotagcamera.geotagginglocationonphoto.stamp.buildStampSpec
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data object Processing : CaptureUiState
    data object AwaitingSignature : CaptureUiState
    data class Saved(val filePath: String) : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

/** Drives the viewfinder's "Locating… → Location locked · ±N m" chip. */
sealed interface LocationChipState {
    data object Locating : LocationChipState
    data class Locked(val accuracyMeters: Float?) : LocationChipState
    data object Unavailable : LocationChipState
}

/** Which extra capture geometry to apply — 1:1 has no CameraX constant, so it's a post-capture crop. */
enum class CaptureAspect { RATIO_4_3, RATIO_16_9, RATIO_1_1 }

/**
 * Everything the live viewfinder overlay needs, resolved once when a fresh fix
 * lands. The map tile bitmap here is the exact one threaded into the burn-in
 * at capture, never re-fetched — so the saved photo can't show a tile the user
 * never saw live.
 */
data class LiveStampData(
    val fix: LocationFix,
    val geocode: AddressParts?,
    val mapTile: Bitmap?,
    val weather: WeatherReading?
)

/**
 * Owns the full shutter-to-saved-row pipeline plus the live viewfinder state
 * (location chip, WYSIWYG stamp spec). Capture snapshots the already-resolved
 * live data instead of re-fetching, so what's framed is what burns in and the
 * shutter isn't blocked waiting on GPS a second time.
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val geocoderRepository = GeocoderRepository(application, AppDatabase.get(application).geoCacheDao())
    private val tileRepository = TileMapRepository(application)
    private val weatherRepository = WeatherRepository()
    private val stampPreferences = StampPreferences(application)
    private val photoDao = AppDatabase.get(application).photoDao()

    val stampFields: StateFlow<StampFields> = stampPreferences.fields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampFields())

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _locationChip = MutableStateFlow<LocationChipState>(LocationChipState.Locating)
    val locationChip: StateFlow<LocationChipState> = _locationChip.asStateFlow()

    private val _liveData = MutableStateFlow<LiveStampData?>(null)

    private val _lastCaptureUri = MutableStateFlow<String?>(null)
    val lastCaptureUri: StateFlow<String?> = _lastCaptureUri.asStateFlow()

    /** The live overlay spec — the same [StampSpec] type the burn-in draws, so the preview is exact. */
    val liveSpec: StateFlow<StampSpec?> =
        combine(stampFields, _liveData) { fields, data ->
            if (data == null) null else buildStampSpec(
                fix = data.fix,
                addressParts = data.geocode,
                capturedAtEpochMs = System.currentTimeMillis(),
                fields = fields,
                mapTile = if (fields.showMap) data.mapTile else null,
                orgLogo = null,
                hasSignature = false,
                weatherChipText = data.weather?.chipText
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var pendingFile: File? = null
    private var pendingSquareCrop: Boolean = false

    /** Fetch a fresh fix and resolve address / map tile / weather for the live overlay. Safe to call repeatedly. */
    fun refreshLocation() {
        viewModelScope.launch {
            _locationChip.value = LocationChipState.Locating
            val fix = locationProvider.getFreshFix()
            if (fix == null) {
                _locationChip.value = LocationChipState.Unavailable
                return@launch
            }
            _locationChip.value = LocationChipState.Locked(fix.accuracyMeters)

            val fields = stampFields.value
            val geocode = runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
            val tile = if (fields.showMap) {
                withContext(Dispatchers.IO) { runCatching { tileRepository.mapThumbnail(fix.latitude, fix.longitude) }.getOrNull() }
            } else null
            val weather = if (fields.showWeather) {
                runCatching { weatherRepository.current(fix.latitude, fix.longitude) }.getOrNull()
            } else null

            _liveData.value = LiveStampData(fix, geocode, tile, weather)
        }
    }

    fun updatePosition(anchor: StampAnchor) {
        val current = stampFields.value
        if (current.position == anchor) return
        viewModelScope.launch { stampPreferences.update(current.copy(position = anchor)) }
    }

    fun capture(imageCapture: ImageCapture, aspect: CaptureAspect) {
        if (_uiState.value != CaptureUiState.Idle) return
        val context = getApplication<Application>()

        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        val squareCrop = aspect == CaptureAspect.RATIO_1_1

        _uiState.value = CaptureUiState.Processing
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (stampFields.value.requireSignature) {
                        pendingFile = file
                        pendingSquareCrop = squareCrop
                        _uiState.value = CaptureUiState.AwaitingSignature
                    } else {
                        processCapturedPhoto(file, signature = null, squareCrop = squareCrop)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = CaptureUiState.Error("Capture failed: ${exception.message}")
                }
            }
        )
    }

    fun submitSignature(signature: Bitmap?) {
        val file = pendingFile ?: return
        pendingFile = null
        _uiState.value = CaptureUiState.Processing
        processCapturedPhoto(file, signature, pendingSquareCrop)
    }

    fun acknowledgeMessage() {
        _uiState.value = CaptureUiState.Idle
    }

    private fun processCapturedPhoto(file: File, signature: Bitmap?, squareCrop: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val capturedAtEpochMs = System.currentTimeMillis()

            // Prefer the already-resolved live snapshot (fix + geocode + the exact
            // map tile the user saw); only pay for a fresh fetch if it isn't ready.
            val snapshot = _liveData.value
            val fix = snapshot?.fix ?: locationProvider.getFreshFix()
            if (fix == null) {
                withContext(Dispatchers.IO) { file.delete() }
                _uiState.value = CaptureUiState.Error("Couldn't get a GPS fix. Move to open sky and try again.")
                return@launch
            }
            val geocode = snapshot?.geocode
                ?: runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
            val fields = stampFields.value

            val mediaUri = withContext(Dispatchers.IO) {
                var upright = loadUprightBitmap(file)
                if (squareCrop) {
                    val cropped = centerSquare(upright)
                    if (cropped !== upright) upright.recycle()
                    upright = cropped
                }
                val stamped = StampRenderer.stamp(
                    context = context,
                    source = upright,
                    fix = fix,
                    addressParts = geocode,
                    capturedAtEpochMs = capturedAtEpochMs,
                    fields = fields,
                    mapTile = if (fields.showMap) snapshot?.mapTile else null,
                    orgLogo = null,
                    hasSignature = signature != null,
                    weatherChipText = snapshot?.weather?.chipText
                )
                if (stamped !== upright) upright.recycle()

                val signed = signature?.let { SignatureOverlay.apply(stamped, it) } ?: stamped
                if (signed !== stamped) stamped.recycle()
                if (signature != null && signature !== signed) signature.recycle()

                FileOutputStream(file).use { out -> signed.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                signed.recycle()

                // Sign the canonical image first — the hash is blind to metadata
                // (see JpegCanonical), so embedding the proof below can't invalidate
                // it — then carry the proof in EXIF UserComment and mirror it in XMP
                // so any device can verify this photo, not just the one that shot it.
                val integrity = PhotoIntegrity.sign(file)
                val proof = UserCommentCodec.encode(integrity, capturedAtEpochMs)
                ExifWriter.write(file, fix, capturedAtEpochMs, proof)
                XmpWriter.write(file, proof)

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
                            address = geocode?.addressLine,
                            addressFromCache = geocode?.fromCache ?: false,
                            orgLabel = fields.orgLabel.ifBlank { null },
                            sha256Hash = integrity.sha256Hex,
                            signatureBase64 = integrity.signatureBase64,
                            signingKeyAlias = integrity.keyAlias,
                            fieldWorkerSignature = signature != null
                        )
                    )
                }
                uri
            }

            if (mediaUri == null) {
                _uiState.value = CaptureUiState.Error("Couldn't save photo to gallery.")
                return@launch
            }

            _lastCaptureUri.value = mediaUri.toString()
            _uiState.value = CaptureUiState.Saved(mediaUri.toString())
        }
    }

    /** Center-crops to the largest centered square, for the 1:1 aspect option (no native CameraX 1:1). */
    private fun centerSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (bitmap.width == bitmap.height) return bitmap
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, left, top, side, side)
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
