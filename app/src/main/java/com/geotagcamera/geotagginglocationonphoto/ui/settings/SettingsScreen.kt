package com.geotagcamera.geotagginglocationonphoto.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields

private data class StampToggle(
    val label: String,
    val description: String,
    val isChecked: (StampFields) -> Boolean,
    val apply: (StampFields, Boolean) -> StampFields
)

private val TOGGLES = listOf(
    StampToggle("Coordinates", "Latitude and longitude", { it.showCoordinates }, { f, v -> f.copy(showCoordinates = v) }),
    StampToggle("Address", "Reverse-geocoded address", { it.showAddress }, { f, v -> f.copy(showAddress = v) }),
    StampToggle("Timestamp", "Date and time of capture", { it.showTimestamp }, { f, v -> f.copy(showTimestamp = v) }),
    StampToggle("Altitude", "Height above sea level", { it.showAltitude }, { f, v -> f.copy(showAltitude = v) }),
    StampToggle("Accuracy", "GPS fix accuracy radius", { it.showAccuracy }, { f, v -> f.copy(showAccuracy = v) }),
    StampToggle("Bearing", "Compass direction", { it.showBearing }, { f, v -> f.copy(showBearing = v) })
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Stamp fields",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(TOGGLES) { toggle ->
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

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Organization",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = fields.orgLabel,
                    onValueChange = { value -> viewModel.update { it.copy(orgLabel = value) } },
                    label = { Text("College, company or project name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
