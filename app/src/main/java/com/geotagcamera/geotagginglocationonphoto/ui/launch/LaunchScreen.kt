package com.geotagcamera.geotagginglocationonphoto.ui.launch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotagcamera.geotagginglocationonphoto.location.LocationProvider
import com.geotagcamera.geotagginglocationonphoto.ui.onboarding.OnboardingPreferences
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.hasCapturePermissions
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFFF5F7F8)
private val AccentVerified = Color(0xFF56CB98)

/**
 * The identity moment (design section 05, screen 01). Shows the brand mark
 * with a pulsing ring and the tagline, warms last-known GPS in the background
 * so the first capture is fast, then routes on. Holds no ImageCapture — it is
 * structurally incapable of taking a photo — and never blocks on the warm.
 */
@Composable
fun LaunchScreen(onNavigateToOnboarding: () -> Unit, onNavigateToCapture: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Fire-and-forget GPS warm; harmless (null) if location isn't granted yet.
        if (hasCapturePermissions(context)) {
            launch(Dispatchers.Default) { runCatching { LocationProvider(context).getFreshFix() } }
        }
        val hasCompleted = withContext(Dispatchers.IO) { OnboardingPreferences(context).hasCompletedOnboarding.first() }
        kotlinx.coroutines.delay(700)
        if (hasCompleted) onNavigateToCapture() else onNavigateToOnboarding()
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseValue"
    )

    GeoTagChromeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BrandMark(pulse)
                }
                Spacer(Modifier.height(28.dp))
                Text("GeoTag Camera", color = Ink, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "No ads. No paywall. We know, it's suspicious.",
                    color = Ink.copy(alpha = 0.55f),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BrandMark(pulse: Float) {
    Canvas(modifier = Modifier.size(96.dp)) {
        val w = size.minDimension
        // Pulsing ring: expands and fades out.
        val ringRadius = (w * 0.42f) + (w * 0.22f) * pulse
        drawCircle(
            color = AccentVerified.copy(alpha = 0.35f * (1f - pulse)),
            radius = ringRadius,
            style = Stroke(width = w * 0.02f)
        )
        // Lens frame: rounded square.
        val inset = w * 0.24f
        val frameSize = w - inset * 2
        drawRoundRect(
            color = Ink,
            topLeft = Offset(inset, inset),
            size = Size(frameSize, frameSize),
            cornerRadius = CornerRadius(frameSize * 0.32f),
            style = Stroke(width = w * 0.045f)
        )
        // Aperture dot, dead centre.
        drawCircle(color = AccentVerified, radius = w * 0.075f, center = Offset(w / 2f, w / 2f))
    }
}
