package com.geotagcamera.geotagginglocationonphoto.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geotagcamera.geotagginglocationonphoto.ui.capture.CaptureScreen
import com.geotagcamera.geotagginglocationonphoto.ui.detail.PhotoDetailScreen
import com.geotagcamera.geotagginglocationonphoto.ui.gallery.GalleryScreen
import com.geotagcamera.geotagginglocationonphoto.ui.launch.LaunchScreen
import com.geotagcamera.geotagginglocationonphoto.ui.legal.AboutLegalScreen
import com.geotagcamera.geotagginglocationonphoto.ui.onboarding.PermissionPrimerScreen
import com.geotagcamera.geotagginglocationonphoto.ui.settings.SettingsScreen
import com.geotagcamera.geotagginglocationonphoto.ui.verify.VerifyScreen

/**
 * Route layout, per the plan at docs/progress.md (session 3, Phase 0):
 * launch (start) -> onboarding (first-run only) -> the 3-tab shell (bottom
 * bar shown only for these) -> sibling top-level destinations for photo
 * detail, verify, and about/legal, none of which show the bottom bar.
 */
private sealed class Tab(val route: String, val label: String, val emoji: String) {
    data object Capture : Tab("capture", "Capture", "📷")
    data object Gallery : Tab("gallery", "Gallery", "🖼")
    data object Settings : Tab("settings", "Settings", "⚙")
}

private val tabs = listOf(Tab.Capture, Tab.Gallery, Tab.Settings)
private val tabRoutes = tabs.map { it.route }.toSet()

private object Routes {
    const val LAUNCH = "launch"
    const val ONBOARDING = "onboarding"
    const val PHOTO_DETAIL = "photoDetail/{photoId}"
    const val VERIFY = "verify?uri={uri}"
    const val ABOUT_LEGAL = "aboutLegal"

    fun photoDetail(photoId: Long) = "photoDetail/$photoId"
    fun verify(uri: String? = null) = if (uri != null) "verify?uri=$uri" else "verify"
}

@Composable
fun GeoTagCameraApp(shareUri: String? = null, onShareConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabRoutes

    // A shared "verify this photo" image is held here (not threaded through the
    // route's query arg, which can't safely carry a content:// Uri) and read by
    // the verify destination once, then cleared.
    var pendingVerifyUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shareUri) {
        if (!shareUri.isNullOrBlank()) {
            pendingVerifyUri = shareUri
            navController.navigate(Routes.verify())
            onShareConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.emoji) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val contentModifier = if (showBottomBar) Modifier.padding(padding) else Modifier

        NavHost(
            navController = navController,
            startDestination = Routes.LAUNCH,
            modifier = contentModifier
        ) {
            composable(Routes.LAUNCH) {
                LaunchScreen(
                    onNavigateToOnboarding = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.LAUNCH) { inclusive = true }
                        }
                    },
                    onNavigateToCapture = {
                        navController.navigate(Tab.Capture.route) {
                            popUpTo(Routes.LAUNCH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.ONBOARDING) {
                PermissionPrimerScreen(
                    onContinue = {
                        navController.navigate(Tab.Capture.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            // Capture/Gallery/Settings keep their current signatures for now — Phase 5 (Capture),
            // Phase 9 (Gallery), and Phase 10 (Settings) are what actually wire real navigation
            // triggers (gallery shortcut, photo detail, verify FAB, about/legal link) into these
            // routes, which already exist and are reachable in the graph starting now.
            composable(Tab.Capture.route) { CaptureScreen() }
            composable(Tab.Gallery.route) { GalleryScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                route = Routes.PHOTO_DETAIL,
                arguments = listOf(navArgument("photoId") { type = NavType.LongType })
            ) { entry ->
                val photoId = entry.arguments?.getLong("photoId") ?: return@composable
                PhotoDetailScreen(photoId = photoId, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.VERIFY,
                arguments = listOf(navArgument("uri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { entry ->
                val uri = entry.arguments?.getString("uri") ?: pendingVerifyUri
                VerifyScreen(uri = uri, onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT_LEGAL) {
                AboutLegalScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
