package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.hardware.display.DisplayManager
import android.util.Rational
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor
import com.geotagcamera.geotagginglocationonphoto.stamp.StampOverlay
import com.geotagcamera.geotagginglocationonphoto.stamp.StampTemplate
import com.geotagcamera.geotagginglocationonphoto.ui.common.anchorForFraction
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.CAPTURE_PERMISSIONS
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.hasCameraPermission
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.hasLocationPermission
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.openAppSettings
import com.geotagcamera.geotagginglocationonphoto.ui.review.ReviewScreen
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ChromeTokens
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ProofColors

// Chrome tokens (design system section 03). Only the shutter is ever a filled control.
private val ChromeBase = ChromeTokens.Base
private val Glass = ChromeTokens.Glass
private val GlassBorder = ChromeTokens.GlassBorder
private val Ink = ChromeTokens.Ink
private val InkMuted = ChromeTokens.InkMuted
private val AccentVerified = ProofColors.Verified
private val AccentPending = ProofColors.Pending
private val AccentTamper = ProofColors.Tamper

/** Caps the saved photo at ~12 MP: plenty for evidence, and a 50 MP decode would not fit in memory. */
private val MAX_CAPTURE_4_3 = Size(4032, 3024)
private val MAX_CAPTURE_16_9 = Size(4032, 2268)

/** Zoom chip + shutter row, with breathing room. The frame shrinks before these do. */
private val MIN_CONTROLS_HEIGHT = 196.dp

@Composable
fun CaptureScreen(
    onOpenGallery: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: CaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(hasCameraPermission(context)) }
    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }
    // True once the system stops showing the dialog ("Don't ask again" or two denials).
    var cameraBlocked by remember { mutableStateOf(false) }
    var locationBlocked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = hasCameraPermission(context)
        hasLocation = hasLocationPermission(context)
        val activity = context.findActivity()
        if (activity != null) {
            cameraBlocked = !hasCamera &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            locationBlocked = !hasLocation &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Ask once per visit to the camera, not on every return from another tab:
    // repeated prompts turn a single "no" into a permanent block on API 30+.
    var askedForPermissions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedForPermissions && (!hasCamera || !hasLocation)) {
            askedForPermissions = true
            launcher.launch(CAPTURE_PERMISSIONS)
        }
    }
    // Back from system Settings with a permission flipped: pick it up immediately.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCamera = hasCameraPermission(context)
        hasLocation = hasLocationPermission(context)
    }

    GeoTagChromeTheme {
        if (hasCamera) {
            CameraContent(
                viewModel = viewModel,
                hasLocation = hasLocation,
                onRequestLocation = {
                    if (locationBlocked) openAppSettings(context) else launcher.launch(CAPTURE_PERMISSIONS)
                },
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings
            )
        } else {
            CameraPermissionScreen(
                blocked = cameraBlocked,
                onRequest = { launcher.launch(CAPTURE_PERMISSIONS) },
                onOpenSettings = { openAppSettings(context) },
                onOpenGallery = onOpenGallery
            )
        }
    }
}

@Composable
private fun CameraContent(
    viewModel: CaptureViewModel,
    hasLocation: Boolean,
    onRequestLocation: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationChip by viewModel.locationChip.collectAsStateWithLifecycle()
    val liveSpec by viewModel.liveSpec.collectAsStateWithLifecycle()
    val lastCaptureUri by viewModel.lastCaptureUri.collectAsStateWithLifecycle()
    val autoDismiss by viewModel.autoDismissReview.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val captureNote by viewModel.captureNote.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var showContextSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    // Phones keep the camera screen portrait, like the stock camera: the layout
    // never jumps, only the icons and the live stamp turn with the phone.
    // Tablets and unfolded foldables keep free rotation and the side-rail layout.
    val compactWindow = configuration.smallestScreenWidthDp < 600
    DisposableEffect(compactWindow) {
        val activity = context.findActivity()
        val before = activity?.requestedOrientation
        if (compactWindow) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var lensFacing by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    var aspect by remember { mutableStateOf(CaptureAspect.RATIO_4_3) }
    var gridOn by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var reticle by remember { mutableStateOf<Offset?>(null) }
    var dragAnchor by remember { mutableStateOf<StampAnchor?>(null) }
    // What this phone's camera can actually do, read after binding.
    var hasFlash by remember { mutableStateOf(false) }
    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }
    var availableModes by remember { mutableStateOf(listOf(ExtensionMode.NONE)) }
    var cameraMode by remember { mutableIntStateOf(ExtensionMode.NONE) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    // Exposure compensation, shown for a few seconds after tap-to-focus.
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureVisibleAt by remember { mutableLongStateOf(0L) }

    val previewView = remember {
        PreviewView(context).apply {
            // The viewfinder box has exactly the capture's aspect ratio, so
            // FILL_CENTER shows the same framing the saved photo gets (and for
            // 1:1 the same centre crop the pipeline applies).
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView-backed preview re-attaches without the ~1-2s black flash
            // a SurfaceView shows when returning to this screen from another tab.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageCapture = remember(aspect) {
        ImageCapture.Builder()
            .setResolutionSelector(captureResolution(aspect))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // Keep the saved photo upright however the phone is held, including with
    // system auto-rotate off (the activity doesn't rotate then, so the display
    // rotation alone would be wrong). The same reading turns the on-screen
    // icons so they stay upright, like the stock camera.
    var deviceRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    val displayRotation = rememberDisplayRotation()
    // With auto-rotate on, the UI already turns with the phone, so the screen's
    // rotation is the truth for the photo, the frame and the icons alike. Only
    // with auto-rotate off does the motion sensor decide how the photo is saved.
    var autoRotate by remember { mutableStateOf(isAutoRotateOn(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { autoRotate = isAutoRotateOn(context) }
    val captureRotation = if (autoRotate && !compactWindow) displayRotation else deviceRotation
    LaunchedEffect(imageCapture, captureRotation) { imageCapture.targetRotation = captureRotation }
    DisposableEffect(imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val rotation = when (degrees) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                deviceRotation = rotation
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    val iconAngle = animateFloatAsState(
        targetValue = uprightAngle(captureRotation, displayRotation),
        animationSpec = tween(250),
        label = "iconAngle"
    )
    // Read in the draw phase only: the animation never recomposes the screen.
    val iconRotation = Modifier.graphicsLayer { rotationZ = iconAngle.value }
    // The live stamp turns too, so it shows exactly where it will sit on the
    // saved (rotated) photo. Snapped, not animated: its frame swaps width and height.
    val stampAngle = uprightAngle(captureRotation, displayRotation)

    // Freeze-frame: on a ratio or lens change, the last preview frame stays on
    // screen (centre-cropped into the animating frame) until the rebound camera
    // streams again, so there is never a black flash or a stretched frame.
    var frozenFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    fun freeze() {
        if (frozenFrame == null) frozenFrame = runCatching { previewView.bitmap?.asImageBitmap() }.getOrNull()
    }
    DisposableEffect(previewView, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING) frozenFrame = null
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }
    LaunchedEffect(frozenFrame) {
        // Never leave a stale frame up if the camera fails to come back.
        if (frozenFrame != null) { delay(2500); frozenFrame = null }
    }

    val shutterScale = remember { Animatable(1f) }
    val shutterFlash = remember { Animatable(0f) }

    // Location streams only while this screen is at least STARTED.
    LaunchedEffect(lifecycleOwner, hasLocation) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.trackLocation(hasLocation) }
    }

    LaunchedEffect(flashMode, imageCapture, hasFlash) {
        imageCapture.flashMode = if (hasFlash) flashMode else ImageCapture.FLASH_MODE_OFF
    }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Exactly the use cases this screen bound, so rebinding and leaving only
    // ever touch our own (never another Capture entry's).
    val bound = remember { BoundUseCases() }
    val latestFrame by rememberUpdatedState(frameSize)
    // Rebind on a meaningful frame-shape change only, not on every pixel of layout.
    val frameShape = if (frameSize.height > 0) (frameSize.width * 100) / frameSize.height else 0
    LaunchedEffect(lensFacing, imageCapture, cameraMode, frameShape, displayRotation) {
        val frame = latestFrame
        if (frame.width <= 0 || frame.height <= 0) return@LaunchedEffect
        // Right after an aspect change the frame is still the old shape for one
        // layout pass; wait for it instead of binding twice.
        viewfinderRatio(aspect, isLandscape)?.let { expected ->
            if (kotlin.math.abs(frame.width.toFloat() / frame.height - expected) > 0.03f) return@LaunchedEffect
        }
        val p = try {
            context.getCameraProvider()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("The camera isn't available right now.")
            return@LaunchedEffect
        }
        provider = p
        hasFrontCamera = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)

        // The phone maker's modes: offered only if this camera stack provides them.
        val ext = extensionsManager ?: try {
            context.getExtensionsManager(p).also { extensionsManager = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        ensureActive()
        availableModes = listOf(ExtensionMode.NONE) + NATIVE_MODES.filter { mode ->
            ext != null && runCatching { ext.isExtensionAvailable(lensFacing, mode) }.getOrDefault(false)
        }
        if (cameraMode !in availableModes) {
            cameraMode = ExtensionMode.NONE
            return@LaunchedEffect
        }
        val selector = if (cameraMode != ExtensionMode.NONE && ext != null) {
            ext.getExtensionEnabledCameraSelector(lensFacing, cameraMode)
        } else lensFacing

        val preview = Preview.Builder()
            .setResolutionSelector(previewResolution(aspect))
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        // The ViewPort is the on-screen frame: CameraX crops the saved photo to
        // exactly this region, for every ratio including 1:1 and Full.
        val viewPort = ViewPort.Builder(Rational(frame.width, frame.height), displayRotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(imageCapture)
            .build()
        val result = runCatching {
            bound.unbindFrom(p)
            p.bindToLifecycle(lifecycleOwner, selector, group).also { bound.set(preview, imageCapture) }
        }
        result.onSuccess { cam ->
            camera = cam
            hasFlash = cam.cameraInfo.hasFlashUnit()
            exposureIndex = cam.cameraInfo.exposureState.exposureCompensationIndex
            val zs = cam.cameraInfo.zoomState.value
            minZoom = zs?.minZoomRatio ?: 1f
            maxZoom = zs?.maxZoomRatio ?: 1f
            zoomRatio = zs?.zoomRatio ?: 1f
        }.onFailure {
            camera = null
            when {
                cameraMode != ExtensionMode.NONE -> cameraMode = ExtensionMode.NONE
                lensFacing != CameraSelector.DEFAULT_BACK_CAMERA -> lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
                else -> snackbarHostState.showSnackbar("Couldn't open the camera. Close other camera apps and try again.")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { provider?.let { bound.unbindFrom(it) } }
    }

    LaunchedEffect(reticle) {
        if (reticle != null) {
            delay(1200)
            reticle = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.notices.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is CaptureUiState.Error) {
            viewModel.acknowledgeMessage()
            snackbarHostState.showSnackbar(state.message)
        }
    }

    // Auto-dismiss the review takeover if the user turned it on (off by default).
    LaunchedEffect(uiState, autoDismiss) {
        if (autoDismiss && uiState is CaptureUiState.Review) {
            delay(4000)
            viewModel.dismissReview()
        }
    }

    fun applyZoom(target: Float) {
        val clamped = target.coerceIn(minZoom, maxZoom)
        zoomRatio = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    val onShutter: () -> Unit = {
        scope.launch {
            shutterScale.animateTo(0.9f, tween(90))
            shutterScale.animateTo(1f, tween(90))
        }
        scope.launch {
            shutterFlash.snapTo(0.35f)
            shutterFlash.animateTo(0f, tween(260))
        }
        viewModel.capture(imageCapture, aspect, mirror = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA)
    }
    val onAspect: () -> Unit = {
        freeze()
        aspect = nextAspect(aspect)
    }
    val onSwitch: () -> Unit = {
        freeze()
        lensFacing = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    val shutterEnabled = uiState is CaptureUiState.Idle && camera != null

    val viewfinder: @Composable (Modifier) -> Unit = { modifier ->
        Viewfinder(
            previewView = previewView,
            // Portrait sizes the frame itself (animated); landscape keeps the ratio modifier.
            ratio = if (isLandscape) viewfinderRatio(aspect, true) else null,
            onFrameSize = { frameSize = it },
            gridOn = gridOn,
            reticle = reticle,
            flashAlpha = { shutterFlash.value },
            liveSpec = liveSpec?.let { spec -> dragAnchor?.let { spec.copy(anchor = it) } ?: spec },
            dragging = dragAnchor != null,
            locationChip = locationChip,
            onLocationChipClick = onRequestLocation,
            processing = uiState is CaptureUiState.Processing,
            snackbarHostState = snackbarHostState,
            frozenFrame = frozenFrame,
            stampAngle = stampAngle,
            onTap = { offset ->
                reticle = offset
                exposureVisibleAt = System.currentTimeMillis()
                camera?.let { cam ->
                    val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                }
            },
            onPinch = { factor -> applyZoom(zoomRatio * factor) },
            exposure = camera?.cameraInfo?.exposureState?.takeIf { it.isExposureCompensationSupported }?.let { state ->
                ExposureUi(
                    index = exposureIndex,
                    range = state.exposureCompensationRange.lower..state.exposureCompensationRange.upper,
                    visibleSince = exposureVisibleAt,
                    onChange = { i ->
                        exposureIndex = i
                        exposureVisibleAt = System.currentTimeMillis()
                        camera?.cameraControl?.setExposureCompensationIndex(i)
                    }
                )
            },
            onStampDrag = { anchor -> dragAnchor = snapForTemplate(anchor, liveSpec?.template) },
            onStampDrop = {
                dragAnchor?.let { viewModel.updatePosition(it) }
                dragAnchor = null
            },
            modifier = modifier
        )
    }

    Box(Modifier.fillMaxSize().background(ChromeBase)) {
        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                SideRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                ) {
                    ControlButtons(
                        iconRotation, hasFlash, flashMode, aspect, gridOn,
                        onFlash = { viewModel.setFlashMode(nextFlash(flashMode)) },
                        onAspect = onAspect,
                        onGrid = { gridOn = !gridOn },
                        onSettings = onOpenSettings
                    )
                    GlassIconButton(Icons.Outlined.Folder, "Project and note: ${activeProject?.name ?: "none"}", { showContextSheet = true }, iconRotation)
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
                    contentAlignment = Alignment.Center
                ) { viewfinder(if (viewfinderRatio(aspect, true) == null) Modifier.fillMaxSize() else Modifier) }
                SideRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                ) {
                    ThumbnailControl(lastCaptureUri, onOpenGallery, iconRotation)
                    ZoomPresets(zoomRatio, minZoom, maxZoom, vertical = true, onZoom = { applyZoom(it) })
                    ModePicker(availableModes, cameraMode, vertical = true) { cameraMode = it }
                    ShutterControl(shutterEnabled, { shutterScale.value }, onShutter)
                    SwitchControl(visible = hasFrontCamera, onSwitch = onSwitch, contentModifier = iconRotation)
                }
            }
        } else {
            // Portrait: the frame sits right under the top bar (like the stock
            // camera), and the shutter cluster is centred in whatever height is
            // left below it, never squeezed against the nav bar.
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlButtons(
                        iconRotation, hasFlash, flashMode, aspect, gridOn,
                        onFlash = { viewModel.setFlashMode(nextFlash(flashMode)) },
                        onAspect = onAspect,
                        onGrid = { gridOn = !gridOn },
                        onSettings = onOpenSettings,
                        beforeSettings = { Spacer(Modifier.weight(1f)) }
                    )
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                ) {
                    val ratio = viewfinderRatio(aspect, landscape = false)
                    val roomForFrame = maxHeight - MIN_CONTROLS_HEIGHT
                    val targetHeight = if (ratio == null) roomForFrame else minOf(maxWidth / ratio, roomForFrame)
                    val targetWidth = if (ratio == null) maxWidth else minOf(maxWidth, targetHeight * ratio)
                    // The frame grows or shrinks from the top edge, the way the stock camera does it.
                    val frameSpec = tween<androidx.compose.ui.unit.Dp>(durationMillis = 280, easing = FastOutSlowInEasing)
                    val frameHeight by animateDpAsState(targetHeight, frameSpec, label = "frameHeight")
                    val frameWidth by animateDpAsState(targetWidth, frameSpec, label = "frameWidth")
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth().height(frameHeight),
                            contentAlignment = Alignment.TopCenter
                        ) { viewfinder(Modifier.size(frameWidth, frameHeight)) }
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                        ) {
                            CaptureContextRow(activeProject, captureNote, onClick = { showContextSheet = true }, modifier = Modifier.padding(horizontal = 16.dp))
                            ZoomPresets(zoomRatio, minZoom, maxZoom, vertical = false, onZoom = { applyZoom(it) })
                            ModePicker(availableModes, cameraMode, vertical = false) { cameraMode = it }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ThumbnailControl(lastCaptureUri, onOpenGallery, iconRotation)
                                ShutterControl(shutterEnabled, { shutterScale.value }, onShutter)
                                SwitchControl(visible = hasFrontCamera, onSwitch = onSwitch, contentModifier = iconRotation)
                            }
                        }
                    }
                }
            }
        }

        if (showContextSheet) {
            CaptureContextSheet(
                projects = projects,
                activeProjectId = activeProject?.id,
                note = captureNote,
                onSelectProject = { viewModel.selectProject(it) },
                onCreateProject = { name -> viewModel.createProject(name) },
                onNoteChange = { viewModel.setCaptureNote(it) },
                onDismiss = { showContextSheet = false }
            )
        }

        val awaiting = uiState as? CaptureUiState.AwaitingSignature
        if (awaiting != null) {
            SignatureCaptureDialog(
                required = awaiting.required,
                onConfirm = { bitmap -> viewModel.submitSignature(bitmap) },
                onSkip = { viewModel.submitSignature(null) },
                onDiscard = { viewModel.cancelSignature() }
            )
        }

        (uiState as? CaptureUiState.AwaitingLocation)?.let { waiting ->
            BackHandler { viewModel.discardHeldShot() }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                icon = { Icon(if (waiting.timedOut) Icons.Filled.LocationOff else Icons.Filled.MyLocation, contentDescription = null) },
                title = { Text(if (waiting.timedOut) "Still no GPS fix" else "Waiting for GPS") },
                text = {
                    Column {
                        Text(
                            if (waiting.timedOut) "The photo is kept. Move to open sky and keep waiting, or discard it."
                            else "Your photo is safe. It will be stamped and saved as soon as your location is found."
                        )
                        if (!waiting.timedOut) {
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    if (waiting.timedOut) {
                        androidx.compose.material3.TextButton(onClick = { viewModel.keepWaitingForLocation() }) { Text("Keep waiting") }
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.discardHeldShot() }) { Text("Discard photo") }
                }
            )
        }

        (uiState as? CaptureUiState.Review)?.let { review ->
            BackHandler { viewModel.dismissReview() }
            ReviewScreen(
                review = review,
                onRetake = { viewModel.retake() },
                onDone = { viewModel.dismissReview() },
                onShare = { shareImage(context, review.uri) }
            )
        }
    }
}

@Composable
private fun Viewfinder(
    previewView: PreviewView,
    ratio: Float?,
    onFrameSize: (IntSize) -> Unit,
    gridOn: Boolean,
    reticle: Offset?,
    flashAlpha: () -> Float,
    liveSpec: com.geotagcamera.geotagginglocationonphoto.stamp.StampSpec?,
    dragging: Boolean,
    locationChip: LocationChipState,
    onLocationChipClick: () -> Unit,
    processing: Boolean,
    snackbarHostState: SnackbarHostState,
    onTap: (Offset) -> Unit,
    exposure: ExposureUi?,
    onPinch: (Float) -> Unit,
    onStampDrag: (StampAnchor) -> Unit,
    onStampDrop: () -> Unit,
    modifier: Modifier = Modifier,
    frozenFrame: ImageBitmap? = null,
    stampAngle: Float = 0f
) {
    val currentAngle by rememberUpdatedState(stampAngle)
    val currentOnPinch by rememberUpdatedState(onPinch)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDrag by rememberUpdatedState(onStampDrag)
    val currentOnDrop by rememberUpdatedState(onStampDrop)
    // All gestures live on this one box: children draw only, so the stamp
    // overlay can never swallow a tap-to-focus again.
    Box(
        modifier = modifier
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
            .onSizeChanged(onFrameSize)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { currentOnTap(it) } }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { off -> currentOnDrag(photoAnchor(off.x / size.width, off.y / size.height, currentAngle)) },
                    onDrag = { change, _ ->
                        currentOnDrag(photoAnchor(change.position.x / size.width, change.position.y / size.height, currentAngle))
                    },
                    onDragEnd = { currentOnDrop() },
                    onDragCancel = { currentOnDrop() }
                )
            }
            .pointerInput(Unit) {
                // Two-finger pinch only; a one-finger drag is left alone for the
                // stamp long-press drag above.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                currentOnPinch(zoom)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        frozenFrame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (gridOn || dragging) ThirdsGrid(strong = dragging)
        liveSpec?.let { spec -> RotatedFrame(stampAngle, Modifier.fillMaxSize()) { StampOverlay(spec, Modifier.fillMaxSize()) } }
        reticle?.let { FocusReticle(it) }
        exposure?.let { ExposureSlider(it, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) }

        // The chip always sits where the stamp isn't.
        val chipAlignment = when {
            liveSpec?.template == StampTemplate.BAR && liveSpec.anchor.isTopRow() -> Alignment.BottomStart
            liveSpec?.anchor == StampAnchor.TOP_LEFT -> Alignment.TopEnd
            else -> Alignment.TopStart
        }
        LocationChip(
            state = locationChip,
            onClick = onLocationChipClick,
            modifier = Modifier.align(chipAlignment).padding(12.dp)
        )

        // Shutter feedback: read inside graphicsLayer, so the animation never recomposes the screen.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flashAlpha() }.background(Color.White.copy(alpha = 0.6f)))

        AnimatedVisibility(
            visible = processing,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) { SavingIndicator() }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

/** Exposure compensation in the camera's own steps (its EV range comes from the hardware). */
private data class ExposureUi(
    val index: Int,
    val range: IntRange,
    val visibleSince: Long,
    val onChange: (Int) -> Unit
)

/**
 * A short vertical exposure slider beside the frame. Appears on tap-to-focus,
 * fades out 3 s after the last touch; drag up to brighten, down to darken.
 */
@Composable
private fun ExposureSlider(ui: ExposureUi, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(ui.visibleSince) {
        if (ui.visibleSince == 0L) return@LaunchedEffect
        visible = true
        delay(3000)
        visible = false
    }
    val onChange by rememberUpdatedState(ui.onChange)
    val range by rememberUpdatedState(ui.range)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.WbSunny, contentDescription = "Exposure", tint = AccentPending, modifier = Modifier.size(18.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(160.dp)
                    .clip(CircleShape)
                    .background(Glass)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, _ ->
                            val r = range
                            val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            onChange((r.first + f * (r.last - r.first)).roundToInt())
                        }
                    }
            ) {
                val span = (ui.range.last - ui.range.first).coerceAtLeast(1)
                val f = (ui.index - ui.range.first).toFloat() / span
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (132.dp * (1f - f)) + 4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AccentPending)
                )
            }
        }
    }
}

@Composable
private fun SavingIndicator() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentVerified, strokeWidth = 2.dp)
        Text("Stamping and signing", color = Ink, fontSize = 13.sp)
    }
}

@Composable
private fun SideRail(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.width(96.dp).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) { content() }
}

@Composable
private fun ControlButtons(
    iconModifier: Modifier,
    showFlash: Boolean,
    flashMode: Int,
    aspect: CaptureAspect,
    gridOn: Boolean,
    onFlash: () -> Unit,
    onAspect: () -> Unit,
    onGrid: () -> Unit,
    onSettings: () -> Unit,
    beforeSettings: @Composable () -> Unit = {}
) {
    if (showFlash) GlassIconButton(flashIcon(flashMode), flashLabel(flashMode), onFlash, iconModifier)
    GlassTextButton(aspectLabel(aspect), "Aspect ratio ${aspectLabel(aspect)}", onAspect, iconModifier)
    GlassIconButton(if (gridOn) Icons.Filled.GridOn else Icons.Filled.GridOff, if (gridOn) "Grid on" else "Grid off", onGrid, iconModifier)
    beforeSettings()
    GlassIconButton(Icons.Outlined.Settings, "Settings", onSettings, iconModifier)
}

@Composable
private fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, contentModifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Ink, modifier = contentModifier.size(22.dp))
    }
}

@Composable
private fun GlassTextButton(text: String, description: String, onClick: () -> Unit, contentModifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(64.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = contentModifier)
    }
}

@Composable
private fun LocationChip(state: LocationChipState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (dot, label) = when (state) {
        is LocationChipState.Locating -> AccentPending to "Locating…"
        is LocationChipState.Locked -> AccentVerified to
            ("Location locked" + (state.accuracyMeters?.let { " · ±${it.toInt()} m" } ?: ""))
        is LocationChipState.Unavailable -> AccentTamper to "No GPS fix"
        is LocationChipState.PermissionDenied -> AccentTamper to "Location off · Tap to allow"
    }
    val clickable = state is LocationChipState.PermissionDenied
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .then(if (clickable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (state is LocationChipState.PermissionDenied) Icons.Filled.LocationOff else Icons.Filled.MyLocation,
            contentDescription = null,
            tint = dot,
            modifier = Modifier.size(14.dp)
        )
        Text(label, color = Ink, fontSize = 12.sp)
    }
}

@Composable
private fun ThirdsGrid(strong: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = if (strong) 0.45f else 0.18f)
        val w = size.width; val h = size.height
        val stroke = if (strong) 1.5.dp.toPx() else 1f
        drawLine(line, Offset(0f, h / 3f), Offset(w, h / 3f), stroke)
        drawLine(line, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), stroke)
        drawLine(line, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
        drawLine(line, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), stroke)
    }
}

@Composable
private fun FocusReticle(at: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val r = 36.dp.toPx()
        drawCircle(AccentPending, radius = r, center = at, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(AccentPending, radius = 2.5.dp.toPx(), center = at)
    }
}

/**
 * Zoom presets built from what this lens really offers: 0.5× (or whatever the
 * ultra-wide minimum is) only when the phone has one, then 1×, 2×, 5×, 10×
 * up to the lens maximum. The active chip shows the live ratio while pinching.
 */
@Composable
private fun ZoomPresets(ratio: Float, min: Float, max: Float, vertical: Boolean, onZoom: (Float) -> Unit) {
    if (max <= min) {
        Spacer(Modifier.size(32.dp))
        return
    }
    val presets = buildList {
        if (min < 0.95f) add(min)
        listOf(1f, 2f, 5f, 10f).filter { it in min..max }.forEach { add(it) }
    }.distinct().let { if (vertical) it.take(3) else it } // the side rail has room for three
    val active = presets.minByOrNull { kotlin.math.abs(it - ratio) }
    val content: @Composable () -> Unit = {
        presets.forEach { preset ->
            val selected = preset == active
            val label = if (selected) formatZoom(ratio) else formatZoom(preset)
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .widthIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (selected) Ink.copy(alpha = 0.92f) else Glass)
                    .border(BorderStroke(1.dp, GlassBorder), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Zoom ${formatZoom(preset)}") { onZoom(preset) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) ChromeBase else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (vertical) Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

private fun formatZoom(z: Float): String =
    if (kotlin.math.abs(z - z.roundToInt()) < 0.05f) "${z.roundToInt()}×" else String.format(Locale.US, "%.1f×", z)

/** Photo / Night / HDR, only listing the modes this phone's camera actually provides. */
@Composable
private fun ModePicker(modes: List<Int>, selected: Int, vertical: Boolean, onSelect: (Int) -> Unit) {
    if (modes.size <= 1) return
    val content: @Composable () -> Unit = {
        modes.forEach { mode ->
            val isSel = mode == selected
            Text(
                modeLabel(mode),
                color = if (isSel) AccentPending else InkMuted,
                fontSize = 12.sp,
                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Tab, onClickLabel = modeLabel(mode)) { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

/** The phone maker's own processing modes, in the order the stock camera usually lists them. */
private val NATIVE_MODES = listOf(
    ExtensionMode.AUTO,
    ExtensionMode.NIGHT,
    ExtensionMode.HDR,
    ExtensionMode.BOKEH,
    ExtensionMode.FACE_RETOUCH
)

private fun modeLabel(mode: Int): String = when (mode) {
    ExtensionMode.AUTO -> "AUTO"
    ExtensionMode.NIGHT -> "NIGHT"
    ExtensionMode.HDR -> "HDR"
    ExtensionMode.BOKEH -> "PORTRAIT"
    ExtensionMode.FACE_RETOUCH -> "RETOUCH"
    else -> "PHOTO"
}

@Composable
private fun ThumbnailControl(lastCaptureUri: String?, onClick: () -> Unit, rotation: Modifier = Modifier) {
    Box(
        modifier = rotation
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ChromeTokens.Pill)
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Open gallery", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (lastCaptureUri != null) {
            AsyncImage(
                model = lastCaptureUri,
                contentDescription = "Last photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Gallery", tint = InkMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ShutterControl(enabled: Boolean, scale: () -> Float, onShutter: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                val s = scale()
                scaleX = s; scaleY = s
            }
            .clip(CircleShape)
            .border(BorderStroke(3.5.dp, Color.White), CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Take photo",
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onShutter
            )
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
    )
}

@Composable
private fun SwitchControl(visible: Boolean, onSwitch: () -> Unit, contentModifier: Modifier = Modifier) {
    if (!visible) {
        // Keep the shutter centred when there is no second camera.
        Spacer(Modifier.size(52.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Switch camera", onClick = onSwitch),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Ink, modifier = contentModifier.size(24.dp))
    }
}

@Composable
private fun CameraPermissionScreen(
    blocked: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChromeBase)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.NoPhotography, contentDescription = null, tint = InkMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(20.dp))
            Text("Camera access needed", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (blocked) "Camera access was turned off for GeoTag Camera. Turn it on in Settings to take photos."
                else "GeoTag Camera uses the camera only to take the photo you stamp. Nothing else.",
                color = InkMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = if (blocked) onOpenSettings else onRequest,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = ChromeBase)
            ) { Text(if (blocked) "Open settings" else "Allow camera", fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenGallery,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) { Text("Go to gallery", color = Ink) }
        }
    }
}

private fun captureResolution(aspect: CaptureAspect): ResolutionSelector {
    // Full is cropped from the 16:9 stream, the closest sensor shape to a phone screen.
    val wide = aspect == CaptureAspect.RATIO_16_9 || aspect == CaptureAspect.FULL
    return ResolutionSelector.Builder()
        .setAspectRatioStrategy(
            if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        )
        .setResolutionStrategy(
            ResolutionStrategy(
                if (wide) MAX_CAPTURE_16_9 else MAX_CAPTURE_4_3,
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()
}

private fun previewResolution(aspect: CaptureAspect): ResolutionSelector =
    ResolutionSelector.Builder()
        .setAspectRatioStrategy(
            AspectRatioStrategy(
                if (aspect == CaptureAspect.RATIO_16_9 || aspect == CaptureAspect.FULL) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3,
                AspectRatioStrategy.FALLBACK_RULE_AUTO
            )
        )
        .build()

private fun StampAnchor.isTopRow(): Boolean =
    this == StampAnchor.TOP_LEFT || this == StampAnchor.TOP_CENTER || this == StampAnchor.TOP_RIGHT

/**
 * Where a dragged stamp lands. Bar is a full-width strip, so it only snaps to
 * the top or bottom edge; Minimal can sit in any of the nine cells.
 */
private fun snapForTemplate(anchor: StampAnchor, template: StampTemplate?): StampAnchor = when (template) {
    StampTemplate.BAR -> when (anchor) {
        StampAnchor.TOP_LEFT, StampAnchor.TOP_CENTER, StampAnchor.TOP_RIGHT -> StampAnchor.TOP_CENTER
        else -> StampAnchor.BOTTOM_CENTER
    }
    else -> anchor
}

/**
 * How far to turn on-screen icons so they read upright: the difference
 * between how the phone is physically held and how the UI is laid out,
 * normalised to -180..180 so the animation takes the short way round.
 */
private fun uprightAngle(deviceRotation: Int, displayRotation: Int): Float {
    fun deg(r: Int) = when (r) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    var a = (deg(deviceRotation) - deg(displayRotation)) % 360
    if (a > 180) a -= 360
    if (a <= -180) a += 360
    return a.toFloat()
}

/** Width / height of the on-screen frame for this capture aspect and orientation; null = fill the space. */
private fun viewfinderRatio(aspect: CaptureAspect, landscape: Boolean): Float? {
    val portrait = when (aspect) {
        CaptureAspect.RATIO_4_3 -> 3f / 4f
        CaptureAspect.RATIO_16_9 -> 9f / 16f
        CaptureAspect.RATIO_1_1 -> 1f
        CaptureAspect.FULL -> return null
    }
    return if (landscape) 1f / portrait else portrait
}

private fun nextFlash(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
    else -> ImageCapture.FLASH_MODE_AUTO
}

private fun flashIcon(mode: Int): ImageVector = when (mode) {
    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
    ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
    else -> Icons.Filled.FlashAuto
}

private fun flashLabel(mode: Int): String = when (mode) {
    ImageCapture.FLASH_MODE_ON -> "Flash on"
    ImageCapture.FLASH_MODE_OFF -> "Flash off"
    else -> "Flash auto"
}

private fun nextAspect(a: CaptureAspect): CaptureAspect = when (a) {
    CaptureAspect.RATIO_4_3 -> CaptureAspect.RATIO_16_9
    CaptureAspect.RATIO_16_9 -> CaptureAspect.RATIO_1_1
    CaptureAspect.RATIO_1_1 -> CaptureAspect.FULL
    CaptureAspect.FULL -> CaptureAspect.RATIO_4_3
}

private fun aspectLabel(a: CaptureAspect): String = when (a) {
    CaptureAspect.RATIO_4_3 -> "4:3"
    CaptureAspect.RATIO_16_9 -> "16:9"
    CaptureAspect.RATIO_1_1 -> "1:1"
    CaptureAspect.FULL -> "Full"
}

/** The use cases this screen currently has bound, so it can unbind exactly those. */
private class BoundUseCases {
    private var useCases: Array<UseCase> = emptyArray()

    fun set(vararg cases: UseCase) {
        useCases = arrayOf(*cases)
    }

    fun unbindFrom(provider: ProcessCameraProvider) {
        if (useCases.isNotEmpty()) runCatching { provider.unbind(*useCases) }
        useCases = emptyArray()
    }
}

/**
 * The display's rotation as observable state. A 180° flip between the two
 * landscape orientations is not a configuration change, so nothing else would
 * notice it and the photo would be saved upside down.
 */
@Composable
private fun rememberDisplayRotation(): Int {
    val view = LocalView.current
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                val d = view.display ?: return
                if (d.displayId == displayId) rotation = d.rotation
            }
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        dm.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        view.display?.let { rotation = it.rotation }
        onDispose { dm.unregisterDisplayListener(listener) }
    }
    return rotation
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        if (!cont.isActive) return@addListener
        // A broken camera HAL or service throws here; never let it crash the app.
        try {
            cont.resume(future.get())
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }, ContextCompat.getMainExecutor(this))
}

private suspend fun Context.getExtensionsManager(provider: ProcessCameraProvider): ExtensionsManager =
    suspendCancellableCoroutine { cont ->
        val future = ExtensionsManager.getInstanceAsync(this, provider)
        future.addListener(
            {
                if (cont.isActive) {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

/** System auto-rotate (the quick-settings toggle). */
private fun isAutoRotateOn(context: Context): Boolean = runCatching {
    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION) == 1
}.getOrDefault(true)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Lays [content] out in the saved photo's own orientation: for a quarter turn
 * it gets this box's height as its width (and vice versa), then is turned by
 * [angle] around the centre, so a landscape shot's stamp previews along the
 * edge that becomes the photo's bottom.
 */
@Composable
private fun RotatedFrame(angle: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val quarter = (kotlin.math.abs(angle.toInt()) / 90) % 2 == 1
    Layout(content = content, modifier = modifier.graphicsLayer { rotationZ = angle }) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cw = if (quarter) h else w
        val ch = if (quarter) w else h
        val placeable = measurables.firstOrNull()?.measure(androidx.compose.ui.unit.Constraints.fixed(cw, ch))
        layout(w, h) { placeable?.place((w - cw) / 2, (h - ch) / 2) }
    }
}

/** A point on the screen frame, as a fraction, mapped into the turned photo's own frame. */
private fun photoAnchor(fx: Float, fy: Float, angle: Float): StampAnchor = when (((angle.toInt() % 360) + 360) % 360) {
    90 -> anchorForFraction(fy, 1f - fx)
    180 -> anchorForFraction(1f - fx, 1f - fy)
    270 -> anchorForFraction(1f - fy, fx)
    else -> anchorForFraction(fx, fy)
}

private fun shareImage(context: Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photo"))
}
