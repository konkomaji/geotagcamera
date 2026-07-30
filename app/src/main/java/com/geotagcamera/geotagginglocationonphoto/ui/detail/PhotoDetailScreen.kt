package com.geotagcamera.geotagginglocationonphoto.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Stub for now (Phase 0: nav scaffolding). Real content lands in Phase 9:
 * full photo, an in-place Verify result (not a popup), and metadata read
 * from live EXIF rather than PhotoEntity's cached columns, per
 * docs/GeoTag Camera Design System.dc.html section 05, screen 06.
 */
@Composable
fun PhotoDetailScreen(photoId: Long, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Photo detail: $photoId")
    }
}
