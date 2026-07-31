package com.geotagcamera.geotagginglocationonphoto

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geotagcamera.geotagginglocationonphoto.ui.nav.GeoTagCameraApp
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagCameraTheme

class MainActivity : ComponentActivity() {

    // Held above the nav graph so a "verify this photo" share can be consumed exactly once.
    private var shareUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shareUri = extractSharedImageUri(intent)
        setContent {
            GeoTagCameraTheme {
                GeoTagCameraApp(
                    shareUri = shareUri,
                    onShareConsumed = { shareUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareUri = extractSharedImageUri(intent)
    }

    private fun extractSharedImageUri(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return uri?.toString()
    }
}
