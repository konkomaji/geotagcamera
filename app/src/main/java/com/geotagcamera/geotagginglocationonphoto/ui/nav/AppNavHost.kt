package com.geotagcamera.geotagginglocationonphoto.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.geotagcamera.geotagginglocationonphoto.ui.capture.CaptureScreen
import com.geotagcamera.geotagginglocationonphoto.ui.gallery.GalleryScreen
import com.geotagcamera.geotagginglocationonphoto.ui.settings.SettingsScreen

private sealed class Destination(val route: String, val label: String, val emoji: String) {
    data object Capture : Destination("capture", "Capture", "📷")
    data object Gallery : Destination("gallery", "Gallery", "🖼")
    data object Settings : Destination("settings", "Settings", "⚙")
}

private val destinations = listOf(Destination.Capture, Destination.Gallery, Destination.Settings)

@Composable
fun GeoTagCameraApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.emoji) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Capture.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Capture.route) { CaptureScreen() }
            composable(Destination.Gallery.route) { GalleryScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
