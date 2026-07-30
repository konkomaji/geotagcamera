package com.geotagcamera.geotagginglocationonphoto.ui.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.geotagcamera.geotagginglocationonphoto.ui.onboarding.OnboardingPreferences
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import kotlinx.coroutines.flow.first

/**
 * Stub for now (Phase 0: nav scaffolding). Real identity moment, camera
 * pre-warm and pulsing ring land in Phase 11 per
 * docs/GeoTag Camera Design System.dc.html section 05, screen 01. The gating
 * logic below (first-run -> onboarding, otherwise straight to capture) is
 * structural and correct now so later phases only need to swap the visuals.
 */
@Composable
fun LaunchScreen(onNavigateToOnboarding: () -> Unit, onNavigateToCapture: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val hasCompleted = OnboardingPreferences(context).hasCompletedOnboarding.first()
        if (hasCompleted) onNavigateToCapture() else onNavigateToOnboarding()
    }

    GeoTagChromeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("GeoTag Camera", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
