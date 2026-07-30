package com.geotagcamera.geotagginglocationonphoto.ui.legal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Stub for now (Phase 0: nav scaffolding). Real content lands in Phase 11:
 * no-data-collected summary, privacy policy, data safety/permissions,
 * licenses, version/source/report-issue, export and delete-all-data, per
 * docs/GeoTag Camera Design System.dc.html section 05, screen 10.
 */
@Composable
fun AboutLegalScreen(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("About & legal")
    }
}
