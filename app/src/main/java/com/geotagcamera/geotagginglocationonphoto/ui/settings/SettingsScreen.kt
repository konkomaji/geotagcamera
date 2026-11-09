package com.geotagcamera.geotagginglocationonphoto.ui.settings

import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.stamp.LogoDecoder
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampOverlay
import com.geotagcamera.geotagginglocationonphoto.stamp.StampTemplate
import com.geotagcamera.geotagginglocationonphoto.stamp.buildStampSpec
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.geotagcamera.geotagginglocationonphoto.profile.UserProfile

private data class StampToggle(
    val label: String,
    val description: String,
    val isChecked: (StampFields) -> Boolean,
    val apply: (StampFields, Boolean) -> StampFields
)

// The four most-changed fields; the rest sit behind "More fields".
private val SURFACED = listOf(
    StampToggle("Address", "Reverse-geocoded address and Plus Code", { it.showAddress }, { f, v -> f.copy(showAddress = v) }),
    StampToggle("Coordinates", "Latitude and longitude", { it.showCoordinates }, { f, v -> f.copy(showCoordinates = v) }),
    StampToggle("Date & time", "When the photo was taken", { it.showTimestamp }, { f, v -> f.copy(showTimestamp = v) }),
    StampToggle("Map thumbnail", "Small street map (uses network)", { it.showMap }, { f, v -> f.copy(showMap = v) })
)

private val MORE = listOf(
    StampToggle("Country code", "ISO country code beside the place", { it.showCountry }, { f, v -> f.copy(showCountry = v) }),
    StampToggle("GMT offset", "Time-zone offset after the time", { it.showGmtOffset }, { f, v -> f.copy(showGmtOffset = v) }),
    StampToggle("Altitude", "Height above sea level", { it.showAltitude }, { f, v -> f.copy(showAltitude = v) }),
    StampToggle("Accuracy", "GPS accuracy radius", { it.showAccuracy }, { f, v -> f.copy(showAccuracy = v) }),
    StampToggle("Bearing", "Compass direction the camera faces", { it.showBearing }, { f, v -> f.copy(showBearing = v) }),
    StampToggle("Weather", "Temperature and conditions (uses network)", { it.showWeather }, { f, v -> f.copy(showWeather = v) }),
    StampToggle("Organisation name", "Your organisation's name in the footer", { it.showOrgLabelToggle }, { f, v -> f.copy(showOrgLabelToggle = v) }),
    StampToggle("Organisation logo", "Your logo in the footer", { it.showOrgLogo }, { f, v -> f.copy(showOrgLogo = v) }),
    StampToggle("Signed mark", "Shows SIGNED when a signature is attached", { it.showSignatureField }, { f, v -> f.copy(showSignatureField = v) }),
    StampToggle("App mark", "Small GeoTag Camera mark", { it.showBrandMark }, { f, v -> f.copy(showBrandMark = v) }),
    StampToggle("Custom text", "Your fixed line from Organisation", { it.showCustomText }, { f, v -> f.copy(showCustomText = v) })
)

/** A fixed, recognisable sample so the preview shows every enabled field. */
private val SAMPLE_FIX = LocationFix(
    latitude = 22.572645, longitude = 88.363892, altitudeMeters = 11.0,
    accuracyMeters = 4f, bearingDegrees = 135f, timestampEpochMs = 0L
)
private val SAMPLE_ADDRESS = AddressParts(
    place = "Kolkata, West Bengal",
    addressLine = "BBD Bagh, Kolkata, West Bengal, 700001",
    countryName = "India", countryCode = "IN", fromCache = false
)

@Composable
fun SettingsScreen(
    onOpenVerify: () -> Unit = {},
    onOpenAboutLegal: () -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val loaded by viewModel.fields.collectAsStateWithLifecycle()
    val autoDismiss by viewModel.autoDismissReview.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    AppScaffold(title = "Settings") { padding ->
        val fields = loaded
        if (fields == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@AppScaffold
        }
        SettingsContent(
            fields = fields,
            autoDismiss = autoDismiss,
            profile = profile,
            contentPadding = padding,
            viewModel = viewModel,
            onOpenVerify = onOpenVerify,
            onOpenAboutLegal = onOpenAboutLegal,
            onOpenOfflineMaps = onOpenOfflineMaps,
            onOpenReport = onOpenReport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    fields: StampFields,
    autoDismiss: Boolean,
    profile: UserProfile,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel,
    onOpenVerify: () -> Unit,
    onOpenAboutLegal: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenReport: () -> Unit
) {
    var showAll by rememberSaveable(profile) { mutableStateOf(profile.showsAllSettings) }
    val pickLogo = rememberPhotoPicker { uri -> viewModel.setOrgLogo(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StampPreview(fields)

        SettingsCard("Set up for") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                UserProfile.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = p == profile,
                        onClick = { if (p != profile) viewModel.setProfile(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, UserProfile.entries.size),
                        label = { Text(p.title) }
                    )
                }
            }
            Text(
                profile.summary + " Switching applies its recommended stamp fields.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        SettingsCard("Stamp style") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                StampTemplate.entries.forEachIndexed { i, template ->
                    SegmentedButton(
                        selected = template == fields.template,
                        onClick = { viewModel.update { it.copy(template = template) } },
                        shape = SegmentedButtonDefaults.itemShape(i, StampTemplate.entries.size),
                        label = { Text(template.label) }
                    )
                }
            }
            Text(
                when (fields.template) {
                    StampTemplate.BAR -> "Every enabled field in a strip across the top or bottom of the photo."
                    StampTemplate.MINIMAL -> "A small label with coordinates and time only."
                } + " To move it, hold the stamp on the camera screen and drag.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        SettingsCard("Stamp fields") {
            SURFACED.forEach { ToggleRow(it, fields, viewModel) }
            AnimatedVisibility(visible = showAll) {
                Column { MORE.forEach { ToggleRow(it, fields, viewModel) } }
            }
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(if (showAll) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (showAll) "Fewer fields" else "More fields (${MORE.size})")
            }
        }

        SettingsCard("Organisation") {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                // Local text state: the field never waits on a DataStore round trip,
                // so fast typing can't drop characters or move the cursor.
                var orgText by rememberSaveable { mutableStateOf(fields.orgLabel) }
                OutlinedTextField(
                    value = orgText,
                    onValueChange = { value ->
                        orgText = value
                        viewModel.update { it.copy(orgLabel = value) }
                    },
                    label = { Text("College, company or project name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                var customText by rememberSaveable { mutableStateOf(fields.customText) }
                OutlinedTextField(
                    value = customText,
                    onValueChange = { value ->
                        customText = value.take(120)
                        viewModel.update { it.copy(customText = customText) }
                    },
                    label = { Text("Text on every photo (optional)") },
                    supportingText = { Text("For example a contract number or inspector name. Per-shot notes are set on the camera screen.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val logoPath = fields.orgLogoUri
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoPath != null) {
                            AsyncImage(
                                model = File(logoPath),
                                contentDescription = "Organisation logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        } else {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = pickLogo) { Text(if (logoPath != null) "Replace logo" else "Choose logo") }
                    if (logoPath != null) {
                        TextButton(onClick = { viewModel.clearOrgLogo() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }

        SettingsCard("Capture") {
            // Field keeps this one tucked away unless it's already on.
            if (profile.showsAllSettings || fields.requireSignature || showAll) {
                SwitchRow(
                    title = "Require signature",
                    subtitle = "Ask for a signature before each photo is saved",
                    checked = fields.requireSignature,
                    onChange = { checked -> viewModel.update { it.copy(requireSignature = checked) } }
                )
            }
            SwitchRow(
                title = "Auto-close review",
                subtitle = "Go back to the camera 4 seconds after each photo",
                checked = autoDismiss,
                onChange = { checked -> viewModel.setAutoDismissReview(checked) }
            )
        }

        SettingsCard("More") {
            NavRow(Icons.Outlined.Map, "Offline maps", "Save areas so the stamp map works without signal", onOpenOfflineMaps)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            NavRow(Icons.Outlined.VerifiedUser, "Verify a photo", "Check any image's proof, even one you didn't take", onOpenVerify)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            NavRow(Icons.Outlined.Info, "About & legal", "Privacy, terms, licences, delete app data", onOpenAboutLegal)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            NavRow(Icons.Outlined.BugReport, "Report a problem", "Email the developer, with device details if you like", onOpenReport)
            if (BuildConfig.DONATE_URL.isNotEmpty()) {
                val uriHandler = LocalUriHandler.current
                HorizontalDivider(Modifier.padding(start = 56.dp))
                NavRow(Icons.Outlined.LocalCafe, "Buy me a coffee", "Free, no ads, no tracking. Support keeps it that way", {
                    runCatching { uriHandler.openUri(BuildConfig.DONATE_URL) }
                })
            }
        }
    }
}

/** The current stamp settings drawn on a sample frame, exactly as the camera would burn them in. */
@Composable
private fun StampPreview(fields: StampFields) {
    val logo by produceState<android.graphics.Bitmap?>(null, fields.orgLogoUri) {
        value = fields.orgLogoUri?.let { path -> withContext(Dispatchers.IO) { LogoDecoder.decode(path) } }
    }
    val spec = remember(fields, logo) {
        buildStampSpec(
            fix = SAMPLE_FIX,
            addressParts = SAMPLE_ADDRESS,
            capturedAtEpochMs = System.currentTimeMillis(),
            fields = fields,
            mapTile = SampleMap.tile,
            orgLogo = logo,
            hasSignature = fields.requireSignature,
            weatherChipText = "31°C CLEAR"
        )
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF8FB3C9), Color(0xFFC9D6CF), Color(0xFF6F7F62), Color(0xFF4A5544))
                    )
                )
        ) {
            StampOverlay(spec, Modifier.fillMaxSize())
        }
        Text(
            "Preview with a sample location and map",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        content()
    }
}

@Composable
private fun ToggleRow(toggle: StampToggle, fields: StampFields, viewModel: SettingsViewModel) {
    SwitchRow(
        title = toggle.label,
        subtitle = toggle.description,
        checked = toggle.isChecked(fields),
        onChange = { checked -> viewModel.update { toggle.apply(it, checked) } }
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // The whole row toggles, not just the small switch.
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private val StampTemplate.label: String
    get() = when (this) {
        StampTemplate.BAR -> "Bar"
        StampTemplate.MINIMAL -> "Minimal"
    }
