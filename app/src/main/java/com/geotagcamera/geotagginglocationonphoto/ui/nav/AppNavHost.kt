package com.geotagcamera.geotagginglocationonphoto.ui.nav

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.geotagcamera.geotagginglocationonphoto.ui.theme.SystemBarIcons
import com.geotagcamera.geotagginglocationonphoto.ui.verify.VerifyScreen

/**
 * Route layout, per the plan at docs/progress.md (session 3, Phase 0):
 * launch (start) -> onboarding (first-run only) -> the 3-tab shell (bottom
 * bar shown only for these) -> sibling top-level destinations for photo
 * detail, verify, and about/legal, none of which show the bottom bar.
 */
private sealed class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Capture : Tab("capture", "Capture", Icons.Outlined.PhotoCamera, Icons.Filled.PhotoCamera)
    data object Gallery : Tab("gallery", "Gallery", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary)
    data object Settings : Tab("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Capture, Tab.Gallery, Tab.Settings)
private val tabRoutes = tabs.map { it.route }.toSet()

private object Routes {
    const val LAUNCH = "launch"
    const val ONBOARDING = "onboarding"
    const val PHOTO_DETAIL = "photoDetail/{photoId}"
    const val VERIFY = "verify?uri={uri}"
    const val ABOUT_LEGAL = "aboutLegal"
    const val OFFLINE_MAPS = "offlineMaps"
    const val LEGAL_DOC = "legal/{doc}"
    const val REPORT = "report?crash={crash}"

    fun legal(doc: com.geotagcamera.geotagginglocationonphoto.ui.legal.LegalDoc) = "legal/${doc.name}"
    fun report(crash: Boolean = false) = "report?crash=$crash"

    fun photoDetail(photoId: Long) = "photoDetail/$photoId"
    fun verify(uri: String? = null) = if (uri != null) "verify?uri=$uri" else "verify"
}

@Composable
fun GeoTagCameraApp(shareUri: String? = null, onShareConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Capture is the immersive dark camera layer — the light tab bar would clash
    // over it, so it's hidden there; Gallery/Settings still show it (with a
    // Capture tab to return), and Capture reaches them via its own controls.
    val showBottomBar = currentRoute in tabRoutes && currentRoute != Tab.Capture.route

    // Launch, onboarding and the camera are always dark; everything else follows the system theme.
    val chromeRoute = currentRoute == null || currentRoute in setOf(Routes.LAUNCH, Routes.ONBOARDING, Tab.Capture.route)
    SystemBarIcons(darkBackground = chromeRoute || isSystemInDarkTheme())

    // After a crash, offer (never force) to email the details, once the camera is up.
    val appContext = androidx.compose.ui.platform.LocalContext.current

    // Google Play build: ask about usage statistics once, on the camera after onboarding.
    val analyticsConsent by com.geotagcamera.geotagginglocationonphoto.analytics.Analytics.consent.collectAsState()
    if (com.geotagcamera.geotagginglocationonphoto.analytics.Analytics.isAvailable &&
        !analyticsConsent.asked && currentRoute == Tab.Capture.route
    ) {
        com.geotagcamera.geotagginglocationonphoto.ui.consent.AnalyticsConsentDialog(onDone = {})
    }
    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        com.geotagcamera.geotagginglocationonphoto.analytics.Analytics.screen(currentRoute)
    }

    // Errors from background work anywhere in the app (see util/SafeCoroutines.kt).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.geotagcamera.geotagginglocationonphoto.util.AppErrors.events.collect { message ->
            android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    var crashOffer by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(currentRoute == Tab.Capture.route) {
        if (currentRoute == Tab.Capture.route && com.geotagcamera.geotagginglocationonphoto.support.CrashCatcher.pending(appContext) != null) crashOffer = true
    }
    if (crashOffer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text("GeoTag Camera closed or froze unexpectedly") },
            text = { androidx.compose.material3.Text("Send the crash details to the developer? You'll see the email before anything is sent. Your photos and locations are not included.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { crashOffer = false; navController.navigate(Routes.report(crash = true)) }) {
                    androidx.compose.material3.Text("Write report")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { crashOffer = false; com.geotagcamera.geotagginglocationonphoto.support.CrashCatcher.clear(appContext) }) {
                    androidx.compose.material3.Text("No, delete it")
                }
            }
        )
    }

    // A shared "verify this photo" image is held here (not threaded through the
    // route's query arg, which can't safely carry a content:// Uri) and read by
    // the verify destination once, then cleared.
    var pendingVerifyUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Tabs share one back stack rooted at Capture. Launch is popped on the way
    // in, so it can't be the popUpTo target (navigation would silently push a
    // second Capture on every round trip, each with its own camera binding).
    fun goToTab(route: String) = navController.navigate(route) {
        popUpTo(Tab.Capture.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    LaunchedEffect(shareUri) {
        if (!shareUri.isNullOrBlank()) {
            pendingVerifyUri = shareUri
            navController.navigate(Routes.verify())
            onShareConsumed()
        }
    }

    // The outer Scaffold only owns the tab bar. Each destination pads itself by
    // the safe-drawing insets (AppScaffold, or Capture's own layout), and the
    // tab bar's height is consumed here so nothing counts the nav bar twice.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { goToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)

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
            composable(Tab.Capture.route) {
                CaptureScreen(
                    onOpenGallery = { goToTab(Tab.Gallery.route) },
                    onOpenSettings = { goToTab(Tab.Settings.route) }
                )
            }
            composable(Tab.Gallery.route) {
                GalleryScreen(
                    onOpenPhoto = { id -> navController.navigate(Routes.photoDetail(id)) },
                    onOpenCamera = { goToTab(Tab.Capture.route) },
                    onVerifyExternal = { uriStr ->
                        pendingVerifyUri = uriStr
                        navController.navigate(Routes.verify())
                    }
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onOpenVerify = { navController.navigate(Routes.verify()) },
                    onOpenAboutLegal = { navController.navigate(Routes.ABOUT_LEGAL) },
                    onOpenOfflineMaps = { navController.navigate(Routes.OFFLINE_MAPS) },
                    onOpenReport = { navController.navigate(Routes.report()) }
                )
            }
            composable(
                route = Routes.PHOTO_DETAIL,
                arguments = listOf(navArgument("photoId") { type = NavType.LongType })
            ) { entry ->
                val photoId = entry.arguments?.getLong("photoId") ?: return@composable
                PhotoDetailScreen(
                    photoId = photoId,
                    onBack = { navController.popBackStack() },
                    onVerify = { uriStr ->
                        pendingVerifyUri = uriStr
                        navController.navigate(Routes.verify())
                    }
                )
            }
            composable(
                route = Routes.VERIFY,
                arguments = listOf(navArgument("uri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { entry ->
                // A pending (shared or picked) Uri is consumed once, so opening Verify
                // later from Settings doesn't re-check an old file whose grant is gone.
                val uri = remember(entry) {
                    entry.arguments?.getString("uri") ?: pendingVerifyUri.also { pendingVerifyUri = null }
                }
                VerifyScreen(uri = uri, onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT_LEGAL) {
                AboutLegalScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDoc = { navController.navigate(Routes.legal(it)) },
                    onReport = { navController.navigate(Routes.report()) }
                )
            }
            composable(Routes.LEGAL_DOC) { entry ->
                val doc = com.geotagcamera.geotagginglocationonphoto.ui.legal.LegalDoc.fromName(entry.arguments?.getString("doc"))
                    ?: com.geotagcamera.geotagginglocationonphoto.ui.legal.LegalDoc.PRIVACY
                com.geotagcamera.geotagginglocationonphoto.ui.legal.LegalDocScreen(doc, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.REPORT,
                arguments = listOf(androidx.navigation.navArgument("crash") { type = androidx.navigation.NavType.BoolType; defaultValue = false })
            ) { entry ->
                com.geotagcamera.geotagginglocationonphoto.support.ReportProblemScreen(
                    onBack = { navController.popBackStack() },
                    fromCrash = entry.arguments?.getBoolean("crash") == true
                )
            }
            composable(Routes.OFFLINE_MAPS) {
                com.geotagcamera.geotagginglocationonphoto.ui.maps.OfflineMapsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
