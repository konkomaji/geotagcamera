package com.geotagcamera.geotagginglocationonphoto.ui.capture

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import com.geotagcamera.geotagginglocationonphoto.util.safeStateIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import com.geotagcamera.geotagginglocationonphoto.data.ProjectWithCount
import com.geotagcamera.geotagginglocationonphoto.data.ProjectEntity
import com.geotagcamera.geotagginglocationonphoto.data.CapturePreferences
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
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
import com.geotagcamera.geotagginglocationonphoto.location.GeocoderRepository
import com.geotagcamera.geotagginglocationonphoto.location.CompassHeading
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.location.LocationProvider
import com.geotagcamera.geotagginglocationonphoto.location.map.MapEngine
import com.geotagcamera.geotagginglocationonphoto.location.WeatherReading
import com.geotagcamera.geotagginglocationonphoto.location.WeatherRepository
import com.geotagcamera.geotagginglocationonphoto.location.ageMs
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import com.geotagcamera.geotagginglocationonphoto.support.AppLog
import com.geotagcamera.geotagginglocationonphoto.security.ProofClaims
import com.geotagcamera.geotagginglocationonphoto.stamp.LogoDecoder
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import com.geotagcamera.geotagginglocationonphoto.stamp.StampRenderer
import com.geotagcamera.geotagginglocationonphoto.stamp.StampSpec
import com.geotagcamera.geotagginglocationonphoto.stamp.buildStampSpec
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import com.geotagcamera.geotagginglocationonphoto.ui.review.ReviewFilename
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data object Processing : CaptureUiState
    /** [required]: the user turned on "Require signature", so the sheet offers no Skip. */
    data class AwaitingSignature(val required: Boolean) : CaptureUiState
    /** Post-capture review takeover: the saved, signed photo plus its quotable name/size. */
    data class Review(val photoId: Long, val uri: String, val filename: String, val sizeBytes: Long) : CaptureUiState
    data class Error(val message: String) : CaptureUiState
    /**
     * Shot taken but no GPS fix yet. The photo is held (not lost) and saved
     * the moment a fix arrives within [CaptureViewModel.LATE_FIX_WINDOW_MS] of
     * the shutter. [timedOut]: the window passed; the user can wait again or discard.
     */
    data class AwaitingLocation(val timedOut: Boolean) : CaptureUiState
}

/** Drives the viewfinder's location chip. */
sealed interface LocationChipState {
    data object Locating : LocationChipState
    data class Locked(val accuracyMeters: Float?) : LocationChipState
    data object Unavailable : LocationChipState
    /** No location permission: photos can't be geotagged until it's granted. */
    data object PermissionDenied : LocationChipState
}

/**
 * Photo shape. The viewfinder is laid out at this ratio and CameraX crops the
 * saved photo to exactly that frame (a ViewPort), so the photo always matches
 * what was on screen. FULL uses all the room the screen has.
 */
enum class CaptureAspect { RATIO_4_3, RATIO_16_9, RATIO_1_1, FULL }

/**
 * The slow-to-resolve parts of the stamp (address, map tile, weather) for one
 * position. Re-resolved only when the user moves far enough, so the live
 * overlay stays cheap; the map tile here is the exact bitmap threaded into the
 * burn-in, so the saved photo can't show a tile the user never saw.
 */
data class LiveStampData(
    val fix: LocationFix,
    val geocode: AddressParts?,
    val mapTile: Bitmap?,
    val weather: WeatherReading?,
    val resolvedAtEpochMs: Long
)

/**
 * Owns the shutter-to-saved-row pipeline and the live viewfinder state.
 *
 * Location streams while the viewfinder is visible ([trackLocation]), so each
 * photo is stamped with a fix at most [MAX_FIX_AGE_MS] old. The capture time
 * is taken at the shutter press, not after the signature sheet closes.
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val compass = CompassHeading(application)
    private val geocoderRepository = GeocoderRepository(application, AppDatabase.get(application).geoCacheDao())
    private val mapEngine = MapEngine(application)
    private val weatherRepository = WeatherRepository()
    private val stampPreferences = StampPreferences(application)
    private val photoDao = AppDatabase.get(application).photoDao()
    private val projectDao = AppDatabase.get(application).projectDao()
    private val capturePreferences = CapturePreferences(application)

    /** The sticky note burned into every stamp until the user changes it. */
    val captureNote: StateFlow<String> = capturePreferences.note
        .safeStateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** All projects (archived last), for the picker. */
    val projects: StateFlow<List<ProjectWithCount>> = projectDao.observeAll()
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The project new photos go into; null = none. A deleted project quietly becomes none. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeProject: StateFlow<ProjectEntity?> = capturePreferences.activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(null) else projectDao.observeById(id) }
        .safeStateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Survives leaving the camera and app restarts; it used to reset to Auto on every return. */
    val flashMode: StateFlow<Int> = capturePreferences.flashMode
        .safeStateIn(viewModelScope, SharingStarted.Eagerly, androidx.camera.core.ImageCapture.FLASH_MODE_AUTO)

    fun setFlashMode(mode: Int) {
        viewModelScope.launchSafely(tag = "Capture") { capturePreferences.setFlashMode(mode) }
    }

    fun setCaptureNote(note: String) {
        viewModelScope.launchSafely(tag = "Capture") { capturePreferences.setNote(note) }
    }

    fun selectProject(id: Long?) {
        viewModelScope.launchSafely(tag = "Capture") { capturePreferences.setActiveProject(id) }
    }

    /** Creates a project and makes it active. Returns an error message, or null. */
    suspend fun createProject(name: String): String? {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return "Give the project a name."
        if (projectDao.countWithName(clean) > 0) return "A project with that name already exists."
        val id = projectDao.insert(ProjectEntity(name = clean, createdAtEpochMs = System.currentTimeMillis()))
        capturePreferences.setActiveProject(id)
        return null
    }

    val stampFields: StateFlow<StampFields> = stampPreferences.fields
        .safeStateIn(viewModelScope, SharingStarted.Eagerly, StampFields())

    /** One-off messages that don't change the screen state (shown as snackbars). */
    private val _notices = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: kotlinx.coroutines.flow.SharedFlow<String> = _notices

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _locationChip = MutableStateFlow<LocationChipState>(LocationChipState.Locating)
    val locationChip: StateFlow<LocationChipState> = _locationChip.asStateFlow()

    private val _latestFix = MutableStateFlow<LocationFix?>(null)
    /** Magnetic heading of the back camera while the bearing field is on, else null. */
    private val _heading = MutableStateFlow<Float?>(null)
    private val _liveData = MutableStateFlow<LiveStampData?>(null)

    /** Newest photo in the index: survives restarts and follows deletes made anywhere. */
    val lastCaptureUri: StateFlow<String?> = photoDao.observeLatestPath()
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Org logo bitmap, decoded from the path Settings stored. Settings writes
     * every new logo under a new file name, so a path change is a logo change.
     */
    val orgLogo: StateFlow<Bitmap?> = stampFields
        .map { it.orgLogoUri }
        .distinctUntilChanged()
        .map { path ->
            path?.let { withContext(Dispatchers.IO) { LogoDecoder.decode(it) } }
        }
        .safeStateIn(viewModelScope, SharingStarted.Eagerly, null)

    val autoDismissReview: StateFlow<Boolean> = stampPreferences.autoDismissReview
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Ticks once per minute boundary so the overlay's clock never freezes. */
    private val minuteTicker: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000L - now % 60_000L)
        }
    }

    /**
     * The live overlay spec: the same [StampSpec] type the burn-in draws.
     * Coordinates come from the newest streamed fix; address, tile and
     * weather from the last resolve near it.
     */
    val liveSpec: StateFlow<StampSpec?> =
        combine(combine(stampFields, captureNote) { f, n -> f to n }, _liveData, combine(_latestFix, _heading) { f, h -> f to h }, orgLogo, minuteTicker) { (fields, note), data, (latest, heading), logo, now ->
            val fix = (latest ?: data?.fix)?.withHeading(heading) ?: return@combine null
            val near = data?.takeIf { distanceMeters(it.fix, fix) <= REUSE_RESOLVE_WITHIN_M }
            runCatching { buildStampSpec(
                fix = fix,
                addressParts = near?.geocode,
                capturedAtEpochMs = now,
                fields = fields,
                mapTile = if (fields.showMap) near?.mapTile else null,
                orgLogo = if (fields.showOrgLogo) logo else null,
                hasSignature = false,
                weatherChipText = near?.weather?.chipText,
                note = note
            ) }.getOrNull()
        }.safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var resolveJob: Job? = null
    private var pending: PendingCapture? = null

    /** [note] and [projectId] are read at the shutter press, like the capture time. */
    private data class PendingCapture(
        val file: File,
        val capturedAtEpochMs: Long,
        val squareCrop: Boolean,
        val note: String? = null,
        val projectId: Long? = null,
        /** Magnetic camera heading at the shutter press, like the capture time. */
        val headingDegrees: Float? = null
    )

    init {
        // Turning the map or weather on in Settings should show up on the next
        // look at the viewfinder, not after the user walks 30 m.
        viewModelScope.launchSafely(tag = "Capture") {
            stampFields.map { it.showMap to it.showWeather }
                .distinctUntilChanged()
                .drop(1)
                .collect { _latestFix.value?.let { fix -> resolve(fix, force = true) } }
        }
    }

    /**
     * Streams location for as long as the caller's coroutine is active. Call it
     * from the screen inside `repeatOnLifecycle(STARTED)` so GPS stops the
     * moment the viewfinder isn't visible.
     */
    suspend fun trackLocation(hasPermission: Boolean) {
        if (!hasPermission) {
            _locationChip.value = LocationChipState.PermissionDenied
            return
        }
        fun hasRecentFix() = _latestFix.value?.let { it.ageMs() <= MAX_FIX_AGE_MS } == true
        // A fix from the last visit doesn't count: the chip only says "locked"
        // for a fix the shutter would actually use.
        if (!hasRecentFix()) _locationChip.value = LocationChipState.Locating
        coroutineScope {
            // No usable fix after a while: say so instead of "Locating…" forever.
            val noFixTimeout = launch {
                delay(NO_FIX_TIMEOUT_MS)
                if (!hasRecentFix()) _locationChip.value = LocationChipState.Unavailable
            }
            // "Locked" must never outlive the fix it describes (GPS lost, location switched off).
            launch {
                while (true) {
                    delay(5_000)
                    if (_locationChip.value is LocationChipState.Locked && !hasRecentFix()) {
                        _locationChip.value = if (locationServicesOn()) LocationChipState.Locating else LocationChipState.Unavailable
                    }
                }
            }
            // Compass only while the bearing field is on: the sensors cost battery.
            launch {
                try {
                    stampFields.map { it.showBearing }.distinctUntilChanged().collectLatest { on ->
                        _heading.value = null
                        if (on) compass.headings().collect { _heading.value = it }
                    }
                } finally {
                    _heading.value = null
                }
            }
            locationProvider.updates().collect { fix ->
                noFixTimeout.cancel()
                _latestFix.value = fix
                _locationChip.value = LocationChipState.Locked(fix.accuracyMeters)
                resolve(fix, force = false)
            }
        }
    }

    /** The stamp's bearing: the camera's compass heading, corrected to true north at [this] fix. */
    private fun LocationFix.withHeading(magneticDegrees: Float?): LocationFix =
        copy(bearingDegrees = magneticDegrees?.let { CompassHeading.toTrueNorth(it, this) })

    private fun locationServicesOn(): Boolean = runCatching {
        val lm = getApplication<Application>().getSystemService(android.location.LocationManager::class.java)
        androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
    }.getOrDefault(true)

    /** Re-resolves address/tile/weather when the user has moved or the data is old. */
    private fun resolve(fix: LocationFix, force: Boolean) {
        val current = _liveData.value
        // A failed lookup (offline start) is retried on the next fix, not in 5 minutes.
        val fresh = current != null && current.geocode != null &&
            distanceMeters(current.fix, fix) <= RERESOLVE_AFTER_M &&
            System.currentTimeMillis() - current.resolvedAtEpochMs <= RERESOLVE_AFTER_MS &&
            // A map that failed (offline, slow network) is retried after a short wait.
            (!stampFields.value.showMap || current.mapTile != null ||
                System.currentTimeMillis() - current.resolvedAtEpochMs <= MAP_RETRY_AFTER_MS)
        if (!force && (fresh || resolveJob?.isActive == true)) return

        resolveJob?.cancel()
        resolveJob = viewModelScope.launchSafely(tag = "Capture") {
            val fields = stampFields.value
            val geocode = runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
            // Publish the address first; tile and weather follow when they land.
            val sameSpot = current?.takeIf { distanceMeters(it.fix, fix) <= REUSE_RESOLVE_WITHIN_M }
            _liveData.value = LiveStampData(fix, geocode, sameSpot?.mapTile, sameSpot?.weather, System.currentTimeMillis())

            val (tile, weather) = coroutineScope {
                val t = async {
                    if (fields.showMap) runCatching { mapEngine.thumbnail(fix.latitude, fix.longitude) }.getOrNull()
                    else null
                }
                val w = async {
                    val reusable = sameSpot?.weather?.takeIf {
                        System.currentTimeMillis() - sameSpot.resolvedAtEpochMs <= WEATHER_REUSE_MS
                    }
                    if (!fields.showWeather) null
                    else reusable ?: runCatching { weatherRepository.current(fix.latitude, fix.longitude) }.getOrNull()
                }
                t.await() to w.await()
            }
            _liveData.value = LiveStampData(fix, geocode, tile, weather, System.currentTimeMillis())
        }
    }

    fun updatePosition(anchor: StampAnchor) {
        if (stampFields.value.position == anchor) return
        viewModelScope.launchSafely(tag = "Capture") { stampPreferences.edit { it.copy(position = anchor) } }
    }

    /**
     * [mirror]: front camera. Its preview is mirrored like a mirror, so the
     * saved photo is flipped to match what the user framed (stamp text is
     * drawn afterwards, on the upright image, so it is never mirrored).
     */
    fun capture(imageCapture: ImageCapture, aspect: CaptureAspect, mirror: Boolean = false) {
        if (_uiState.value != CaptureUiState.Idle) return
        val context = getApplication<Application>()
        // The moment the shutter was pressed is the capture time, for the
        // stamp, the EXIF and the signed proof alike.
        val capturedAt = System.currentTimeMillis()
        // A full phone fails late and silently in the camera HAL; say so up front.
        if (lowOnStorage(context)) {
            _uiState.value = CaptureUiState.Error(STORAGE_FULL)
            return
        }
        val file = File(context.cacheDir, "capture_$capturedAt.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = mirror })
            .build()
        val capture = PendingCapture(
            file, capturedAt, squareCrop = aspect == CaptureAspect.RATIO_1_1,
            note = captureNote.value.trim().ifEmpty { null },
            projectId = activeProject.value?.id,
            headingDegrees = _heading.value
        )

        _uiState.value = CaptureUiState.Processing
        try {
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val fields = stampFields.value
                        if (fields.requireSignature) {
                            pending = capture
                            _uiState.value = CaptureUiState.AwaitingSignature(required = true)
                        } else {
                            processCapturedPhoto(capture, signature = null)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        _uiState.value = CaptureUiState.Error(
                            when (exception.imageCaptureError) {
                                ImageCapture.ERROR_CAMERA_CLOSED -> "The camera was interrupted by another app. Try again."
                                ImageCapture.ERROR_FILE_IO -> if (lowOnStorage(context)) STORAGE_FULL else "Couldn't write the photo. Try again."
                                ImageCapture.ERROR_CAPTURE_FAILED -> "The camera couldn't take the shot. Hold still and try again."
                                else -> "Capture failed. Try again."
                            }
                        )
                    }
                }
            )
        } catch (e: IllegalStateException) {
            // Camera not bound yet (e.g. a tap during a lens switch).
            _uiState.value = CaptureUiState.Error("Camera isn't ready yet.")
        }
    }

    fun submitSignature(signature: Bitmap?) {
        val capture = pending ?: return
        pending = null
        _uiState.value = CaptureUiState.Processing
        processCapturedPhoto(capture, signature)
    }

    /** User backed out of a required signature: the shot is discarded, nothing saved. */
    fun cancelSignature() {
        pending?.file?.delete()
        pending = null
        _uiState.value = CaptureUiState.Idle
    }

    fun acknowledgeMessage() {
        _uiState.value = CaptureUiState.Idle
    }

    /** Leave the review takeover back to the live viewfinder, keeping the photo. */
    fun dismissReview() {
        _uiState.value = CaptureUiState.Idle
    }

    /**
     * Retake: the photo just saved is discarded (gallery file and index row),
     * then the viewfinder comes back. A photo this app saved moments ago is
     * always still owned by it, so no consent prompt is needed.
     */
    fun retake() {
        val review = _uiState.value as? CaptureUiState.Review ?: return
        _uiState.value = CaptureUiState.Idle
        viewModelScope.launchSafely(Dispatchers.IO, tag = "Capture") {
            val outcome = MediaStoreImageSaver.delete(getApplication(), listOf(Uri.parse(review.uri)))
            // Keep the index row if the file couldn't be removed, so it isn't orphaned.
            if (outcome.deleted.isNotEmpty()) photoDao.delete(review.photoId)
        }
    }

    private var heldSignature: Bitmap? = null
    private var waitJob: kotlinx.coroutines.Job? = null

    /** Holds the shot and saves it as soon as a usable fix streams in. */
    private fun holdForLocation(capture: PendingCapture, signature: Bitmap?) {
        pending = capture
        heldSignature = signature
        _uiState.value = CaptureUiState.AwaitingLocation(timedOut = false)
        waitJob?.cancel()
        waitJob = viewModelScope.launchSafely(tag = "Capture") {
            val deadline = capture.capturedAtEpochMs + LATE_FIX_WINDOW_MS
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(10_000L)
            val fix = kotlinx.coroutines.withTimeoutOrNull(remaining) {
                // Streamed fixes, plus an active one-shot request in case streaming stalled.
                launch { runCatching { locationProvider.getFreshFix() }.getOrNull()?.let { _latestFix.value = it } }
                _latestFix.first { it != null && it.ageMs(capture.capturedAtEpochMs) <= MAX_FIX_AGE_MS && it.timestampEpochMs <= deadline }
            }
            if (fix != null) {
                pending = null
                val sig = heldSignature.also { heldSignature = null }
                _uiState.value = CaptureUiState.Processing
                processCapturedPhoto(capture, sig, fix)
            } else {
                _uiState.value = CaptureUiState.AwaitingLocation(timedOut = true)
            }
        }
    }

    /** "Keep waiting" after the window passed: accepts the next fix, whenever it comes. */
    fun keepWaitingForLocation() {
        val capture = pending ?: return
        waitJob?.cancel()
        _uiState.value = CaptureUiState.AwaitingLocation(timedOut = false)
        waitJob = viewModelScope.launchSafely(tag = "Capture") {
            launch { runCatching { locationProvider.getFreshFix() }.getOrNull()?.let { _latestFix.value = it } }
            val fix = kotlinx.coroutines.withTimeoutOrNull(LATE_FIX_WINDOW_MS) {
                _latestFix.first { it != null && it.ageMs() <= MAX_FIX_AGE_MS }
            }
            if (fix != null) {
                pending = null
                val sig = heldSignature.also { heldSignature = null }
                _uiState.value = CaptureUiState.Processing
                processCapturedPhoto(capture, sig, fix)
            } else {
                _uiState.value = CaptureUiState.AwaitingLocation(timedOut = true)
            }
        }
    }

    /** Give up on a held shot: the file is deleted, nothing is saved. */
    fun discardHeldShot() {
        waitJob?.cancel()
        pending?.file?.delete()
        pending = null
        heldSignature?.recycle()
        heldSignature = null
        _uiState.value = CaptureUiState.Idle
    }

    private fun processCapturedPhoto(capture: PendingCapture, signature: Bitmap?, knownFix: LocationFix? = null) {
        val context = getApplication<Application>()
        val file = capture.file
        val capturedAtEpochMs = capture.capturedAtEpochMs
        viewModelScope.launchSafely(tag = "Capture") {
            var held = false
            try {
                val fix = (knownFix ?: pickFix(capturedAtEpochMs))?.withHeading(capture.headingDegrees)
                if (fix == null) {
                    if (_locationChip.value == LocationChipState.PermissionDenied) {
                        _uiState.value = CaptureUiState.Error("Location is off. Allow location to geotag photos.")
                    } else {
                        // Never lose the shot to a slow GPS: hold it and save when a fix lands.
                        held = true
                        holdForLocation(capture, signature)
                    }
                    return@launchSafely
                }
                // Reuse what the viewfinder showed only if it was resolved for
                // this spot; otherwise geocode the actual capture position.
                val near = _liveData.value?.takeIf { distanceMeters(it.fix, fix) <= REUSE_RESOLVE_WITHIN_M }
                val geocode = near?.geocode
                    ?: runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
                val fields = stampPreferences.fields.first()

                val saved = withContext(Dispatchers.IO) {
                    try {
                        saveStampedPhoto(file, capture, fix, geocode, near, fields, signature, capturedAtEpochMs)
                    } catch (e: OutOfMemoryError) {
                        // Never lose the shot to memory: try again at half size.
                        AppLog.w("Capture", "out of memory stamping, retrying at half size")
                        System.gc()
                        saveStampedPhoto(file, capture, fix, geocode, near, fields, signature?.takeUnless { it.isRecycled }, capturedAtEpochMs, sampleSize = 2)
                            ?.also { _notices.tryEmit("Saved at reduced resolution: the phone was low on memory.") }
                    }
                }
                if (saved == null) {
                    _uiState.value = CaptureUiState.Error("Couldn't save the photo to your gallery.")
                    return@launchSafely
                }
                _uiState.value = saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _uiState.value = CaptureUiState.Error("Not enough memory to process this photo. Close other apps and try again.")
            } catch (e: java.io.IOException) {
                _uiState.value = CaptureUiState.Error(if (lowOnStorage(context)) STORAGE_FULL else "Couldn't save the photo. Try again.")
            } catch (e: Exception) {
                AppLog.e("Capture", "processing failed", e)
                _uiState.value = CaptureUiState.Error("Couldn't process the photo. Try again.")
            } finally {
                if (!held) withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { file.delete() }
            }
        }
    }

    /** The newest streamed fix if it is recent enough, else a fresh one-shot request. */
    private suspend fun pickFix(capturedAtEpochMs: Long): LocationFix? {
        if (_locationChip.value == LocationChipState.PermissionDenied) return null
        val latest = _latestFix.value
        if (latest != null && latest.ageMs(capturedAtEpochMs) <= MAX_FIX_AGE_MS) return latest
        return locationProvider.getFreshFix()
    }

    private suspend fun saveStampedPhoto(
        file: File,
        capture: PendingCapture,
        fix: LocationFix,
        geocode: AddressParts?,
        near: LiveStampData?,
        fields: StampFields,
        signature: Bitmap?,
        capturedAtEpochMs: Long,
        sampleSize: Int = 1
    ): CaptureUiState.Review? {
        val context = getApplication<Application>()
        var image = loadUprightBitmap(file, sampleSize) ?: return null
        if (capture.squareCrop) image = centerSquare(image)

        // Both draws go into the same bitmap when it is mutable: no extra
        // full-resolution copies (the old pipeline held two at once).
        image = StampRenderer.stamp(
            context = context,
            source = image,
            fix = fix,
            addressParts = geocode,
            capturedAtEpochMs = capturedAtEpochMs,
            fields = fields,
            mapTile = if (fields.showMap) near?.mapTile else null,
            orgLogo = if (fields.showOrgLogo) orgLogo.value else null,
            hasSignature = signature != null,
            weatherChipText = if (fields.showWeather) near?.weather?.chipText else null,
            signature = signature,
            note = capture.note
        ).also { if (it !== image) image.recycle() }
        signature?.recycle()

        FileOutputStream(file).use { out -> image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        image.recycle()

        // Sign the canonical image first — the hash is blind to metadata (see
        // JpegCanonical), so embedding the proof below can't invalidate it —
        // then carry the proof in EXIF UserComment and mirror it in XMP so any
        // device can verify this photo, not just the one that shot it.
        // A Keystore that can't sign must never cost the user the photo: it is
        // saved unsigned, with a notice, and simply shows "no proof" in Verify.
        val integrity = if (fix.isMock) {
            // A signature would vouch for a location a mock-location app made up.
            _notices.tryEmit("Saved without a signature: the location comes from a mock-location app.")
            null
        } else try {
            PhotoIntegrity.sign(file, ProofClaims(fix.latitude, fix.longitude, capturedAtEpochMs))
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("Capture", "signing failed, saving unsigned", e)
            null
        }
        val proof = integrity?.let { UserCommentCodec.encode(it, capturedAtEpochMs) }
        ExifWriter.write(file, fix, capturedAtEpochMs, proof)
        if (proof != null) XmpWriter.write(file, proof)
        else if (!fix.isMock) _notices.tryEmit("Saved without a signature: this phone's secure key store isn't working. The photo can't be verified later.")

        val filename = ReviewFilename.generate(geocode?.place, capturedAtEpochMs)
        val sizeBytes = file.length()
        val uri = MediaStoreImageSaver.save(context, file, filename) ?: return null
        val id = photoDao.insert(
            PhotoEntity(
                filePath = uri.toString(),
                capturedAtEpochMs = capturedAtEpochMs,
                latitude = fix.latitude,
                longitude = fix.longitude,
                altitudeMeters = fix.altitudeMeters,
                accuracyMeters = fix.accuracyMeters,
                bearingDegrees = fix.bearingDegrees,
                address = geocode?.addressLine,
                addressFromCache = geocode?.fromCache ?: false,
                orgLabel = fields.orgLabel.ifBlank { null },
                sha256Hash = integrity?.sha256Hex ?: PhotoIntegrity.hashOnly(file),
                signatureBase64 = integrity?.signatureBase64.orEmpty(),
                signingKeyAlias = integrity?.keyAlias.orEmpty(),
                fieldWorkerSignature = signature != null,
                projectId = capture.projectId,
                note = capture.note,
                placeName = geocode?.place
            )
        )
        com.geotagcamera.geotagginglocationonphoto.analytics.Analytics.event(
            "photo_captured",
            mapOf(
                "template" to fields.template.name, "map" to fields.showMap, "weather" to fields.showWeather,
                "bearing" to fields.showBearing, "signed" to (integrity != null), "worker_signature" to (signature != null),
                "in_project" to (capture.projectId != null)
            )
        )
        return CaptureUiState.Review(id, uri.toString(), filename, sizeBytes)
    }

    override fun onCleared() {
        waitJob?.cancel()
        heldSignature?.recycle()
        pending?.file?.delete()
        super.onCleared()
    }

    private companion object {
        /** A streamed fix older than this at shutter time is not trusted; a fresh one is requested. */
        const val MAX_FIX_AGE_MS = 30_000L
        const val NO_FIX_TIMEOUT_MS = 15_000L
        /** A fix this soon after the shutter still describes where the photo was taken. */
        const val LATE_FIX_WINDOW_MS = 60_000L
        const val RERESOLVE_AFTER_M = 30f
        const val RERESOLVE_AFTER_MS = 5 * 60_000L
        const val MAP_RETRY_AFTER_MS = 20_000L
        const val STORAGE_FULL = "Phone storage is almost full. Free up some space to keep shooting."
        /** Room for the full-size working copy, the stamped JPEG and the gallery copy. */
        private const val MIN_FREE_BYTES = 60L * 1024 * 1024

        fun lowOnStorage(context: android.content.Context): Boolean = runCatching {
            val stat = android.os.StatFs(context.cacheDir.absolutePath)
            stat.availableBytes < MIN_FREE_BYTES
        }.getOrDefault(false)
        /** Address/tile resolved within this distance of the capture point still describe it. */
        const val REUSE_RESOLVE_WITHIN_M = 50f
        const val WEATHER_REUSE_MS = 10 * 60_000L
        const val JPEG_QUALITY = 92

        fun distanceMeters(a: LocationFix, b: LocationFix): Float {
            val out = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
            return out[0]
        }

        /**
         * Center-crops to a square, a fallback for when the camera's own crop
         * didn't (CameraX's ViewPort normally delivers 1:1 already). Draws into
         * a new mutable bitmap so the stamp can draw in place afterwards.
         */
        fun centerSquare(bitmap: Bitmap): Bitmap {
            if (bitmap.width == bitmap.height) return bitmap
            val side = minOf(bitmap.width, bitmap.height)
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(
                bitmap, -((bitmap.width - side) / 2f), -((bitmap.height - side) / 2f), null
            )
            bitmap.recycle()
            return out
        }

        /** Decodes mutable (so the stamp draws in place) and applies the EXIF orientation. */
        fun loadUprightBitmap(file: File, sampleSize: Int = 1): Bitmap? {
            val orientation = ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            if (matrix.isIdentity) return bitmap

            // Rotate into a new MUTABLE bitmap (Bitmap.createBitmap(src, matrix)
            // returns an immutable one, which would force a third full-size copy
            // when the stamp is drawn).
            val bounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            matrix.mapRect(bounds)
            matrix.postTranslate(-bounds.left, -bounds.top)
            val out = Bitmap.createBitmap(bounds.width().toInt(), bounds.height().toInt(), Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
            return out
        }
    }
}
