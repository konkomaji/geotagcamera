package com.geotagcamera.geotagginglocationonphoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.geotagcamera.geotagginglocationonphoto.ui.capture.CaptureScreen
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeoTagCameraTheme {
                CaptureScreen()
            }
        }
    }
}
