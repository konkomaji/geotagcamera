package com.geotagcamera.geotagginglocationonphoto.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.CAPTURE_PERMISSIONS
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import kotlinx.coroutines.launch

/**
 * First-run primer (design section 05, screen 02). States each permission's
 * actual boundary in plain language before the OS dialog, then requests them.
 * Whatever the user grants, onboarding is marked complete and Capture takes
 * over — Capture re-checks and handles denial itself, so this never traps.
 */
@Composable
fun PermissionPrimerScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun finish() = scope.launch {
        OnboardingPreferences(context).setCompleted()
        onContinue()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { finish() }

    GeoTagChromeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text("Before your first shot", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "What each permission is for, and where it stops.",
                    color = Muted, fontSize = 13.sp
                )
                Spacer(Modifier.height(24.dp))

                PermissionRow("Camera", "To take the photo. Nothing else.")
                PermissionRow(
                    "Location",
                    "To stamp where the photo was taken. Foreground only, never in the background, never uploaded."
                )
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    PermissionRow(
                        "Storage",
                        "Only on older Android (9 and below), only to save the photo into your gallery."
                    )
                }
                PermissionRow(
                    "Network",
                    "Used only by the optional map tile and weather chip, both off by default. Everything else works offline."
                )

                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("No ads. No paywall. We know, it's suspicious.", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "No accounts, no analytics, no ad SDK. Nothing leaves the device unless you turn on a map tile or weather lookup.",
                        color = Muted, fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { launcher.launch(CAPTURE_PERMISSIONS) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(body, color = Muted, fontSize = 12.5.sp)
    }
}

private val Ink = androidx.compose.ui.graphics.Color(0xFFF5F7F8)
private val Muted = androidx.compose.ui.graphics.Color(0x8CF5F7F8)
