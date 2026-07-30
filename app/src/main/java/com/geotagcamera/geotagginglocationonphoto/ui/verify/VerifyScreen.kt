package com.geotagcamera.geotagginglocationonphoto.ui.verify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Stub for now (Phase 0: nav scaffolding). Real content lands in Phase 8:
 * reads the embedded hash+signature+public-key from any image's EXIF
 * UserComment (falling back to the XMP mirror), reachable with zero
 * captures ever taken on this device, per
 * docs/GeoTag Camera Design System.dc.html section 05, screen 09.
 */
@Composable
fun VerifyScreen(uri: String?, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (uri != null) "Verify: $uri" else "Verify a photo")
    }
}
