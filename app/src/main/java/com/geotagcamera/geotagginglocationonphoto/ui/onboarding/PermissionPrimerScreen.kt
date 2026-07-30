package com.geotagcamera.geotagginglocationonphoto.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import kotlinx.coroutines.launch

/**
 * Stub for now (Phase 0: nav scaffolding). Real per-permission rationale,
 * values block and the OS-dialog sequencing land in Phase 11 per
 * docs/GeoTag Camera Design System.dc.html section 05, screen 02. Continuing
 * from here already correctly marks onboarding complete so the gate in
 * LaunchScreen behaves right from day one.
 */
@Composable
fun PermissionPrimerScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    GeoTagChromeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    scope.launch {
                        OnboardingPreferences(context).setCompleted()
                        onContinue()
                    }
                }) {
                    Text("Continue")
                }
            }
        }
    }
}
