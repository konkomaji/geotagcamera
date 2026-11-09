package com.geotagcamera.geotagginglocationonphoto.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.analytics.Analytics

/**
 * Google Play build only. Asks once, after onboarding, and from Settings any
 * time later. Both choices start off: "No thanks" and closing the dialog
 * both mean nothing is collected. Same wording worldwide, which also meets
 * the GDPR/UK GDPR bar of freely given, specific, opt-in consent.
 */
@Composable
fun AnalyticsConsentDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val current = Analytics.consent.value
    var usage by rememberSaveable { mutableStateOf(current.usage) }
    var demographics by rememberSaveable { mutableStateOf(current.demographics) }

    fun save(u: Boolean, d: Boolean) {
        Analytics.setConsent(context, u, d)
        onDone()
    }

    AlertDialog(
        onDismissRequest = { save(current.usage && current.asked, current.demographics && current.asked) },
        title = { Text("Help improve GeoTag Camera") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "With your permission, this Google Play version sends anonymous statistics to Google Analytics, " +
                        "so the developer can see which features are used and on which phones.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Never sent: your photos, locations, addresses, notes or anything you type. No ads, ever.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Choice(
                    "Usage statistics",
                    "Screens opened and features used, with app version and phone model.",
                    usage
                ) { usage = it; if (!it) demographics = false }
                Choice(
                    "Approximate demographics",
                    "Age range, gender and interests, estimated by Google using your phone's advertising ID.",
                    demographics,
                    enabled = usage
                ) { demographics = it }
                Text(
                    "You can change this any time in Settings. The GitHub and F-Droid versions have no analytics at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { save(usage, demographics) }) { Text("Save choice") } },
        dismissButton = { TextButton(onClick = { save(false, false) }) { Text("No thanks") } }
    )
}

@Composable
private fun Choice(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(start = 4.dp))
    }
}
