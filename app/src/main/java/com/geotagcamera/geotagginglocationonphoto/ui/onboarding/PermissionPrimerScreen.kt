package com.geotagcamera.geotagginglocationonphoto.ui.onboarding

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotagcamera.geotagginglocationonphoto.profile.UserProfile
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import com.geotagcamera.geotagginglocationonphoto.ui.permissions.CAPTURE_PERMISSIONS
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ChromeTokens
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagChromeTheme
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Motion
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Radii
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * First run, two steps. 1: who is this for (Field or Professional), which
 * sets sensible defaults and how much Settings shows. 2: what each
 * permission is for, in plain words, before the OS dialog. Whatever the user
 * grants, onboarding completes and Capture handles denial itself, so this
 * never traps anyone.
 */
@Composable
fun PermissionPrimerScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var profile by rememberSaveable { mutableStateOf(UserProfile.FIELD) }

    // A settings write that fails must not trap the user in onboarding:
    // log it, keep the defaults, and go on to the camera.
    fun finish() = scope.launchSafely(tag = "Onboarding", onError = { onContinue() }) {
        val prefs = OnboardingPreferences(context)
        prefs.setProfile(profile)
        StampPreferences(context).edit { profile.applyDefaults(it) }
        prefs.setCompleted()
        onContinue()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { finish() }

    BackHandler(enabled = step == 1) { step = 0 }

    GeoTagChromeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK)) },
                label = "onboardingStep"
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.XL, vertical = Spacing.XXL),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(Modifier.widthIn(max = 560.dp)) {
                        if (current == 0) ProfileStep(profile, onSelect = { profile = it }, onNext = { step = 1 })
                        else PermissionStep(onContinue = { launcher.launch(CAPTURE_PERMISSIONS) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStep(selected: UserProfile, onSelect: (UserProfile) -> Unit, onNext: () -> Unit) {
    Text("How will you use it?", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("This only sets defaults. You can change it any time in Settings.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(Spacing.XL))
    UserProfile.entries.forEach { profile ->
        ProfileOption(
            profile = profile,
            icon = if (profile == UserProfile.FIELD) Icons.Outlined.School else Icons.Outlined.Engineering,
            selected = profile == selected,
            onClick = { onSelect(profile) }
        )
        Spacer(Modifier.height(Spacing.M))
    }
    Spacer(Modifier.height(Spacing.L))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
}

@Composable
private fun ProfileOption(profile: UserProfile, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radii.M)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Ink else ChromeTokens.Outline), shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(Spacing.L)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Spacing.M))
            Text(profile.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null)
        }
        Spacer(Modifier.height(Spacing.XS))
        Text(profile.summary, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(Spacing.S))
        profile.details.forEach { line ->
            Row(Modifier.padding(top = Spacing.XXS)) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(Spacing.S))
                Text(line, color = Ink, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun PermissionStep(onContinue: () -> Unit) {
    Text("Before your first shot", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("What each permission is for, and where it stops.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(Spacing.XL))

    PermissionRow("Camera", "To take the photo. Nothing else.")
    PermissionRow(
        "Location",
        "To stamp where the photo was taken. Only while the camera is open, never in the background. Precise or approximate both work."
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        PermissionRow("Storage", "Only on older Android (9 and below), only to save the photo into your gallery.")
    }
    PermissionRow(
        "Network",
        "For the small map on the stamp (OpenStreetMap, drawn on your phone and kept for offline use), the optional weather, and your phone's own address lookup. Taking, stamping and signing photos works offline."
    )

    Spacer(Modifier.height(20.dp))
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("No ads. No paywall. No accounts.", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            "No analytics, no ad SDK. Your photos never leave the device unless you share them.",
            color = Muted, fontSize = 12.sp
        )
    }

    Spacer(Modifier.height(28.dp))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
}

@Composable
private fun PermissionRow(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(body, color = Muted, fontSize = 12.5.sp)
    }
}

private val Ink = ChromeTokens.Ink
private val Muted = ChromeTokens.Muted
