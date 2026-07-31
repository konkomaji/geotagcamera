package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPainter
import com.geotagcamera.geotagginglocationonphoto.ui.review.ReviewScreen
import com.geotagcamera.geotagginglocationonphoto.ui.common.anchorForFraction
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.CAPTURE_PERMISSIONS
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.hasCapturePermissions
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Chrome "glass" tokens (design system section 03). Only the shutter is ever a filled control.
private val Glass = Color(0x9E141619)
private val GlassBorder = Color(0x21FFFFFF)
private val Ink = Color(0xFFF5F7F8)
private val AccentVerified = Color(0xFF56CB98)
private val AccentPending = Color(0xFFE5A24B)

@Composable
fun CaptureScreen(viewModel: CaptureViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(hasCapturePermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermissions = result.values.all { it } }

    LaunchedEffect(Unit) {
        if (!hasPermissions) launcher.launch(CAPTURE_PERMISSIONS)
    }

    GeoTagChromeTheme {
        if (hasPermissions) {
            CameraContent(viewModel)
        } else {
            PermissionRationale(onRequest = { launcher.launch(CAPTURE_PERMISSIONS) })
        }
    }
}

@Composable
private fun CameraContent(viewModel: CaptureViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationChip by viewModel.locationChip.collectAsStateWithLifecycle()
    val liveSpec by viewModel.liveSpec.collectAsStateWithLifecycle()
    val lastCaptureUri by viewModel.lastCaptureUri.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Landscape (design section 10: controls float clear of the letterbox, never
    // fixed widths). The shutter cluster becomes a right-edge vertical rail and
    // the zoom rail moves to the left so the two don't collide.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Camera control state
    var lensFacing by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var aspect by remember { mutableStateOf(CaptureAspect.RATIO_4_3) }
    var gridOn by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }
    var reticle by remember { mutableStateOf<Offset?>(null) }
    var dragAnchor by remember { mutableStateOf<com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    // ImageCapture rebuilds when the aspect ratio changes (it's a build-time property). 1:1 captures 4:3 then crops.
    val imageCapture = remember(aspect) {
        val ratio = if (aspect == CaptureAspect.RATIO_16_9) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        ImageCapture.Builder().setTargetAspectRatio(ratio).build()
    }

    // Shutter feedback
    val shutterScale = remember { Animatable(1f) }
    val shutterFlash = remember { Animatable(0f) }

    LaunchedEffect(Unit) { viewModel.refreshLocation() }

    LaunchedEffect(flashMode, imageCapture) { imageCapture.flashMode = flashMode }

    DisposableEffect(lensFacing, imageCapture) {
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        var provider: ProcessCameraProvider? = null
        scope.launch {
            provider = context.getCameraProvider().also { p ->
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, lensFacing, preview, imageCapture).also { cam ->
                    val zs = cam.cameraInfo.zoomState.value
                    minZoom = zs?.minZoomRatio ?: 1f
                    maxZoom = zs?.maxZoomRatio ?: 1f
                    zoomRatio = zs?.zoomRatio ?: 1f
                }
            }
        }
        onDispose { provider?.unbindAll() }
    }

    // Reticle auto-fade after 1.2s
    LaunchedEffect(reticle) {
        if (reticle != null) {
            kotlinx.coroutines.delay(1200)
            reticle = null
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is CaptureUiState.Error) {
            snackbarHostState.showSnackbar(state.message)
            viewModel.acknowledgeMessage()
        }
    }

    fun applyZoom(target: Float) {
        val clamped = target.coerceIn(minZoom, maxZoom)
        zoomRatio = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
    ) {
            // Viewfinder + tap-to-focus + pinch-to-zoom (bottom layer)
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { offset ->
                            reticle = offset
                            val cam = camera ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    }
                    .pointerInput(camera, minZoom, maxZoom) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) applyZoom(zoomRatio * zoom)
                        }
                    }
            )

            if (gridOn) RuleOfThirdsGrid()
            EdgeScrims()

            TopControlRow(
                flashMode = flashMode,
                aspect = aspect,
                gridOn = gridOn,
                onFlash = { flashMode = nextFlash(flashMode) },
                onAspect = { aspect = nextAspect(aspect) },
                onGrid = { gridOn = !gridOn },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            LocationChip(
                state = locationChip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 84.dp)
            )

            if (maxZoom > minZoom) {
                ZoomRail(
                    ratio = zoomRatio,
                    min = minZoom,
                    max = maxZoom,
                    onRatio = { applyZoom(it) },
                    modifier = Modifier.align(if (isLandscape) Alignment.CenterStart else Alignment.CenterEnd)
                )
            }

            reticle?.let { FocusReticle(it) }

            // Live WYSIWYG stamp overlay + drag-to-reposition (long-press then drag)
            liveSpec?.let { spec ->
                val textMeasurer = rememberTextMeasurer()
                val shown = dragAnchor?.let { spec.copy(anchor = it) } ?: spec
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off ->
                                    dragAnchor = anchorForFraction(off.x / size.width, off.y / size.height)
                                },
                                onDrag = { change, _ ->
                                    dragAnchor = anchorForFraction(change.position.x / size.width, change.position.y / size.height)
                                },
                                onDragEnd = { dragAnchor?.let { viewModel.updatePosition(it) }; dragAnchor = null },
                                onDragCancel = { dragAnchor = null }
                            )
                        }
                ) {
                    StampPainter.draw(this, shown, textMeasurer)
                }
                Text(
                    "DRAG TO REPOSITION · LIVE PREVIEW",
                    color = Ink.copy(alpha = 0.5f),
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = if (isLandscape) 16.dp else 156.dp)
                )
            }

            // Bottom control bar: thumbnail · shutter · switch
            BottomBar(
                lastCaptureUri = lastCaptureUri,
                shutterEnabled = uiState is CaptureUiState.Idle,
                shutterScale = shutterScale.value,
                onShutter = {
                    scope.launch {
                        shutterScale.animateTo(0.9f, tween(90))
                        shutterScale.animateTo(1f, tween(90))
                    }
                    scope.launch {
                        shutterFlash.snapTo(0.12f)
                        shutterFlash.animateTo(0f, tween(220))
                    }
                    viewModel.capture(imageCapture, aspect)
                },
                onSwitch = {
                    lensFacing = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA)
                        CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                },
                vertical = isLandscape,
                modifier = Modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
            )

            // Frame flash (12% white) on shutter
            if (shutterFlash.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(shutterFlash.value)
                        .background(Color.White)
                )
            }

            if (uiState is CaptureUiState.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentVerified
                )
            }

            if (uiState is CaptureUiState.AwaitingSignature) {
                SignatureCaptureDialog(
                    onConfirm = { bitmap -> viewModel.submitSignature(bitmap) },
                    onSkip = { viewModel.submitSignature(null) }
                )
            }

            (uiState as? CaptureUiState.Review)?.let { review ->
                ReviewScreen(
                    review = review,
                    onDismiss = { viewModel.dismissReview() },
                    onShare = { shareImage(context, review.uri) }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) 24.dp else 160.dp)
            )
    }
}

@Composable
private fun RuleOfThirdsGrid() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.16f)
        val w = size.width; val h = size.height
        drawLine(line, Offset(0f, h / 3f), Offset(w, h / 3f), 1f)
        drawLine(line, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), 1f)
        drawLine(line, Offset(w / 3f, 0f), Offset(w / 3f, h), 1f)
        drawLine(line, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), 1f)
    }
}

@Composable
private fun EdgeScrims() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val top = Color(0x8C08090A)
        drawRect(top, size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.12f))
        drawRect(
            Color(0xB808090A),
            topLeft = Offset(0f, size.height * 0.82f),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.18f)
        )
    }
}

@Composable
private fun TopControlRow(
    flashMode: Int,
    aspect: CaptureAspect,
    gridOn: Boolean,
    onFlash: () -> Unit,
    onAspect: () -> Unit,
    onGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        GlassPill(text = flashLabel(flashMode), onClick = onFlash)
        GlassPill(text = aspectLabel(aspect), onClick = onAspect)
        GlassPill(text = "Grid", onClick = onGrid, filled = gridOn)
    }
}

@Composable
private fun GlassPill(text: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (filled) Ink else Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) Color(0xFF0C0E10) else Ink, fontSize = 12.sp)
    }
}

@Composable
private fun LocationChip(state: LocationChipState, modifier: Modifier = Modifier) {
    val (dot, label) = when (state) {
        is LocationChipState.Locating -> AccentPending to "Locating…"
        is LocationChipState.Locked -> AccentVerified to
            ("Location locked" + (state.accuracyMeters?.let { " · ±${it.toInt()} m" } ?: ""))
        is LocationChipState.Unavailable -> Color(0xFFE24947) to "No GPS fix"
    }
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(label, color = Ink, fontSize = 11.5.sp)
    }
}

@Composable
private fun FocusReticle(at: Offset) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val r = 37.dp.toPx()
        drawRect(
            color = AccentPending,
            topLeft = Offset(at.x - r, at.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(AccentPending, radius = 2.5.dp.toPx(), center = at)
    }
}

@Composable
private fun ZoomRail(
    ratio: Float,
    min: Float,
    max: Float,
    onRatio: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = ((ratio - min) / (max - min)).coerceIn(0f, 1f)
    Column(
        modifier = modifier.padding(end = 16.dp).width(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("%.1f×".format(ratio), color = Ink, fontSize = 10.5.sp)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(150.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .pointerInput(min, max) {
                    detectVerticalDragGestures { change, _ ->
                        val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        onRatio(min + f * (max - min))
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (150.dp * (1f - fraction)))
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Ink)
            )
        }
    }
}

@Composable
private fun BottomBar(
    lastCaptureUri: String?,
    shutterEnabled: Boolean,
    shutterScale: Float,
    onShutter: () -> Unit,
    onSwitch: () -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    if (vertical) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(150.dp)
                .padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ThumbnailControl(lastCaptureUri)
            ShutterControl(shutterEnabled, shutterScale, onShutter)
            SwitchControl(onSwitch)
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThumbnailControl(lastCaptureUri)
            ShutterControl(shutterEnabled, shutterScale, onShutter)
            SwitchControl(onSwitch)
        }
    }
}

@Composable
private fun ThumbnailControl(lastCaptureUri: String?) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2C3237))
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
    ) {
        lastCaptureUri?.let {
            AsyncImage(model = it, contentDescription = "Last capture", modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ShutterControl(shutterEnabled: Boolean, shutterScale: Float, onShutter: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(shutterScale)
            .clip(CircleShape)
            .border(BorderStroke(3.5.dp, Color.White), CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (shutterEnabled) Color.White else Color.White.copy(alpha = 0.4f))
            .pointerInput(shutterEnabled) {
                detectTapGestures { if (shutterEnabled) onShutter() }
            }
    )
}

@Composable
private fun SwitchControl(onSwitch: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(BorderStroke(1.dp, GlassBorder), CircleShape)
            .pointerInput(Unit) { detectTapGestures { onSwitch() } },
        contentAlignment = Alignment.Center
    ) {
        Text("⟲", color = Ink, fontSize = 19.sp)
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "GeoTag Camera needs camera and location access to stamp photos with where and when they were taken.",
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(16.dp))
            Button(onClick = onRequest) { Text("Grant permissions") }
        }
    }
}

private fun nextFlash(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
    else -> ImageCapture.FLASH_MODE_AUTO
}

private fun flashLabel(mode: Int): String = when (mode) {
    ImageCapture.FLASH_MODE_ON -> "Flash On"
    ImageCapture.FLASH_MODE_OFF -> "Flash Off"
    else -> "Flash Auto"
}

private fun nextAspect(a: CaptureAspect): CaptureAspect = when (a) {
    CaptureAspect.RATIO_4_3 -> CaptureAspect.RATIO_16_9
    CaptureAspect.RATIO_16_9 -> CaptureAspect.RATIO_1_1
    CaptureAspect.RATIO_1_1 -> CaptureAspect.RATIO_4_3
}

private fun aspectLabel(a: CaptureAspect): String = when (a) {
    CaptureAspect.RATIO_4_3 -> "4:3"
    CaptureAspect.RATIO_16_9 -> "16:9"
    CaptureAspect.RATIO_1_1 -> "1:1"
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}

private fun shareImage(context: Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photo"))
}
