package com.geotagcamera.geotagginglocationonphoto.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val SOURCE_URL = "https://github.com/konkomaji/geotagcamera"

/**
 * About & legal (design section 05, screen 10). Copy is written to match the
 * eventual Play data-safety declaration word for word so the two can't drift,
 * and to never overclaim: a signature proves the image is unedited and which
 * key signed it, never who was holding the phone.
 */
@Composable
fun AboutLegalScreen(onBack: () -> Unit, viewModel: AboutLegalViewModel = viewModel()) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("About & legal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Section("No data collected") {
            Body("No accounts, no analytics, no ad SDK, no crash reporting. Nothing is sent off the device unless you turn on the optional map tile or weather lookup — both off by default, on demand only.")
        }

        Section("What the signature proves") {
            Body("Each photo is hashed and signed with a key on this device. Verifying proves the image is unedited since capture and which key signed it. It does not prove who was holding the phone — no signature can.")
        }

        Section("Location & photos") {
            Body("Location is used only to stamp where a photo was taken, in the foreground, never in the background, never uploaded. Photos are saved to your own gallery and processed only on this device.")
        }

        Section("Optional network features") {
            Body("The map thumbnail uses OpenStreetMap data via Stadia Maps; weather uses Open-Meteo. Each is off by default, fetched on demand for the current location only, and never blocks a capture if offline.")
        }

        Section("Licenses") {
            Body("GeoTag Camera — GPLv3.\nPoppins — SIL Open Font License 1.1.\nRoboto Mono — Apache License 2.0.\nMap data © OpenStreetMap contributors (ODbL), tiles © Stadia Maps.\nWeather — Open-Meteo (CC BY 4.0).")
        }

        Section("Source") {
            Text(
                SOURCE_URL,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Delete all app data", color = MaterialTheme.colorScheme.error) }
        Body("Clears this app's index, caches, logo and settings. Your saved photos stay in your gallery.")
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onConfirm = { viewModel.deleteAllData { showDeleteDialog = false } },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val armed = typed.trim().equals("DELETE", ignoreCase = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all app data?") },
        text = {
            Column {
                Text("This clears the capture index, caches, org logo and settings. Photos already in your gallery are untouched. Type DELETE to confirm.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("Type DELETE") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = armed) {
                Text("Delete", color = if (armed) MaterialTheme.colorScheme.error else Color.Gray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
