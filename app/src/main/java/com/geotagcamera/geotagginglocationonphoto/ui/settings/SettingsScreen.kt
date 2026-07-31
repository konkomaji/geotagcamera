package com.geotagcamera.geotagginglocationonphoto.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampTemplate
import com.geotagcamera.geotagginglocationonphoto.ui.common.NineAnchorGrid
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker
import java.io.File

private data class StampToggle(
    val label: String,
    val description: String,
    val isChecked: (StampFields) -> Boolean,
    val apply: (StampFields, Boolean) -> StampFields
)

// The four surfaced by default; the rest live behind "All 14 fields…".
private val SURFACED = listOf(
    StampToggle("Map", "Small OSM map thumbnail", { it.showMap }, { f, v -> f.copy(showMap = v) }),
    StampToggle("Address", "Reverse-geocoded address", { it.showAddress }, { f, v -> f.copy(showAddress = v) }),
    StampToggle("Coordinates", "Latitude and longitude", { it.showCoordinates }, { f, v -> f.copy(showCoordinates = v) }),
    StampToggle("Date & time", "When the photo was taken", { it.showTimestamp }, { f, v -> f.copy(showTimestamp = v) })
)

private val MORE = listOf(
    StampToggle("Country chip", "ISO country code beside the place", { it.showCountry }, { f, v -> f.copy(showCountry = v) }),
    StampToggle("GMT offset", "Time-zone offset with the timestamp", { it.showGmtOffset }, { f, v -> f.copy(showGmtOffset = v) }),
    StampToggle("Altitude", "Height above sea level", { it.showAltitude }, { f, v -> f.copy(showAltitude = v) }),
    StampToggle("Accuracy", "GPS fix accuracy radius", { it.showAccuracy }, { f, v -> f.copy(showAccuracy = v) }),
    StampToggle("Bearing", "Compass direction", { it.showBearing }, { f, v -> f.copy(showBearing = v) }),
    StampToggle("Weather", "Current temperature and condition (network)", { it.showWeather }, { f, v -> f.copy(showWeather = v) }),
    StampToggle("Organisation label", "Show your org name row", { it.showOrgLabelToggle }, { f, v -> f.copy(showOrgLabelToggle = v) }),
    StampToggle("Organisation logo", "Show your logo in the stamp", { it.showOrgLogo }, { f, v -> f.copy(showOrgLogo = v) }),
    StampToggle("Signature mark", "Show a SIGNED mark when a signature is attached", { it.showSignatureField }, { f, v -> f.copy(showSignatureField = v) }),
    StampToggle("Brand mark", "Small GeoTag Camera mark", { it.showBrandMark }, { f, v -> f.copy(showBrandMark = v) })
)

@Composable
fun SettingsScreen(
    onOpenVerify: () -> Unit = {},
    onOpenAboutLegal: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val autoDismiss by viewModel.autoDismissReview.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }
    val pickLogo = rememberPhotoPicker { uri -> viewModel.setOrgLogo(uri) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SectionHeader("Stamp layout")
        TemplatePicker(fields.template) { t -> viewModel.update { it.copy(template = t) } }

        Text(
            "Position",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
        )
        Box(modifier = Modifier.padding(horizontal = 16.dp).width(150.dp)) {
            NineAnchorGrid(selected = fields.position, onSelect = { a -> viewModel.update { it.copy(position = a) } })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        SectionHeader("Stamp fields")
        SURFACED.forEach { ToggleRow(it, fields, viewModel) }
        TextButton(
            onClick = { showAll = !showAll },
            modifier = Modifier.padding(start = 8.dp)
        ) { Text(if (showAll) "Show fewer fields" else "All 14 fields…") }
        if (showAll) MORE.forEach { ToggleRow(it, fields, viewModel) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        SectionHeader("Organisation")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = fields.orgLabel,
                onValueChange = { value -> viewModel.update { it.copy(orgLabel = value) } },
                label = { Text("College, company or project name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val logoPath = fields.orgLogoUri
                if (logoPath != null) {
                    AsyncImage(
                        model = File(logoPath),
                        contentDescription = "Organisation logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                OutlinedButton(onClick = pickLogo) { Text(if (logoPath != null) "Replace logo" else "Choose logo") }
                if (logoPath != null) {
                    TextButton(onClick = { viewModel.clearOrgLogo() }) { Text("Remove") }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        SectionHeader("Capture defaults")
        ListItem(
            headlineContent = { Text("Require signature before saving") },
            supportingContent = { Text("Prompt to draw a signature after each shot") },
            trailingContent = {
                Switch(
                    checked = fields.requireSignature,
                    onCheckedChange = { checked -> viewModel.update { it.copy(requireSignature = checked) } }
                )
            }
        )
        ListItem(
            headlineContent = { Text("Auto-dismiss review") },
            supportingContent = { Text("Return to the viewfinder a few seconds after each capture") },
            trailingContent = {
                Switch(
                    checked = autoDismiss,
                    onCheckedChange = { checked -> viewModel.setAutoDismissReview(checked) }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        ListItem(
            headlineContent = { Text("Verify a photo") },
            supportingContent = { Text("Check any image's proof, even one you didn't take") },
            modifier = Modifier.clickable(onClick = onOpenVerify)
        )
        ListItem(
            headlineContent = { Text("About & legal") },
            supportingContent = { Text("Privacy, data safety, licenses") },
            modifier = Modifier.clickable(onClick = onOpenAboutLegal)
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ToggleRow(toggle: StampToggle, fields: StampFields, viewModel: SettingsViewModel) {
    ListItem(
        headlineContent = { Text(toggle.label) },
        supportingContent = { Text(toggle.description) },
        trailingContent = {
            Switch(
                checked = toggle.isChecked(fields),
                onCheckedChange = { checked -> viewModel.update { toggle.apply(it, checked) } }
            )
        }
    )
}

@Composable
private fun TemplatePicker(selected: StampTemplate, onSelect: (StampTemplate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StampTemplate.entries.forEach { template ->
            val isSel = template == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(template) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    template.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
