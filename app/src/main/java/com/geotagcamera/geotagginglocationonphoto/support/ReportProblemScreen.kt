package com.geotagcamera.geotagginglocationonphoto.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Write a report and hand it to the phone's email app, addressed to the
 * developer. Shows exactly what will be included; nothing is sent from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemScreen(onBack: () -> Unit, fromCrash: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val crash = remember { if (fromCrash) CrashCatcher.pending(context) else null }

    var kind by rememberSaveable { mutableStateOf(ProblemReport.Kind.PROBLEM) }
    var message by rememberSaveable { mutableStateOf(if (crash != null) "The app closed unexpectedly. What I was doing: " else "") }
    var includeDevice by rememberSaveable { mutableStateOf(true) }
    var includeLog by rememberSaveable { mutableStateOf(false) }
    var includeCrash by rememberSaveable { mutableStateOf(crash != null) }
    var noEmailApp by rememberSaveable { mutableStateOf(false) }
    var contact by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sentId by rememberSaveable { mutableStateOf<String?>(null) }
    val attachments = remember { mutableStateListOf<ReportAttachments.Prepared>() }
    DisposableEffect(Unit) { onDispose { ReportAttachments.clear(context) } }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ReportAttachments.MAX_IMAGES + ReportAttachments.MAX_VIDEOS)
    ) { uris ->
        scope.launch {
            for (uri in uris) {
                when (val r = ReportAttachments.prepare(context, uri)) {
                    is ReportAttachments.Outcome.Ok -> {
                        val video = r.attachment.mime.startsWith("video/")
                        val count = attachments.count { it.mime.startsWith("video/") == video }
                        val limit = if (video) ReportAttachments.MAX_VIDEOS else ReportAttachments.MAX_IMAGES
                        if (count < limit) attachments += r.attachment
                        else { r.attachment.file.delete(); snackbar.showSnackbar("Up to ${ReportAttachments.MAX_IMAGES} screenshots and ${ReportAttachments.MAX_VIDEOS} recording.") }
                    }
                    is ReportAttachments.Outcome.Refused -> snackbar.showSnackbar(r.reason)
                }
            }
        }
    }

    AppScaffold(title = "Report a problem", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.L, vertical = Spacing.S),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                Text(
                    "Goes straight to the developer at ${ProblemReport.EMAIL}. You see everything that is included, and nothing is sent until you press Send.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(Spacing.L))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ProblemReport.Kind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, ProblemReport.Kind.entries.size),
                            label = { Text(k.label) }
                        )
                    }
                }
                Spacer(Modifier.size(Spacing.M))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(4000) },
                    label = { Text(if (kind == ProblemReport.Kind.PROBLEM) "What happened, and what did you expect?" else "Your message") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                )
                Spacer(Modifier.size(Spacing.S))
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it.take(200) },
                    label = { Text("Your email, if you'd like a reply (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(Spacing.S))
                Check("Include device details", ProblemReport.deviceInfo(context).replace("\n", " · "), includeDevice) { includeDevice = it }
                Check("Attach app log", "Recent messages from this app only. Map tile addresses are removed.", includeLog) { includeLog = it }
                if (crash != null) Check("Include crash details", "From the last time the app closed unexpectedly.", includeCrash) { includeCrash = it }
                Spacer(Modifier.size(Spacing.S))
                attachments.forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (a.mime.startsWith("video/")) Icons.Outlined.Videocam else Icons.Outlined.Image,
                            contentDescription = null, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(Spacing.M))
                        Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { a.file.delete(); attachments.remove(a) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove ${a.label}")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    enabled = sentId == null && attachments.size < ReportAttachments.MAX_IMAGES + ReportAttachments.MAX_VIDEOS,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.S))
                    Text("Add screenshot or screen recording")
                }
                Text(
                    "Screenshots are resized and their hidden data (including GPS) removed before sending. " +
                        "Up to ${ReportAttachments.MAX_IMAGES} screenshots and one recording of up to ${ReportAttachments.MAX_VIDEO_BYTES / 1024 / 1024} MB. " +
                        "Screen recordings are fine; a video from the camera may contain where it was filmed. " +
                        "Attachments go with Send report only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.XS)
                )
                Text(
                    "Never included: your photos, locations or capture list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.S)
                )
                if (sentId != null) {
                    Text(
                        "Sent. Thank you, the developer reads every report. Reference: $sentId",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = Spacing.S)
                    )
                }
                Button(
                    onClick = {
                        sending = true
                        scope.launch {
                            val log = if (includeLog) withContext(Dispatchers.IO) { ProblemReport.captureLog(context) } else null
                            val result = ReportSender.send(context, kind, message, includeDevice, if (includeCrash) crash else null, log, contact, attachments.toList())
                            sending = false
                            when (result) {
                                is ReportSender.Result.Sent -> {
                                    sentId = result.id
                                    if (crash != null) CrashCatcher.clear(context)
                                    attachments.clear()
                                    ReportAttachments.clear(context)
                                    snackbar.showSnackbar("Report sent. Thank you.")
                                }
                                is ReportSender.Result.Rejected -> snackbar.showSnackbar(result.message)
                                ReportSender.Result.Offline -> snackbar.showSnackbar("Couldn't reach the report server. Check your connection, or send it with your email app.")
                            }
                        }
                    },
                    enabled = !sending && sentId == null && message.trim().length >= 5,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(Spacing.S))
                    Text(if (sending) "Sending" else "Send report")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val log = if (includeLog) withContext(Dispatchers.IO) { ProblemReport.captureLog(context) } else null
                            val sent = ProblemReport.send(
                                context,
                                ProblemReport.subject(kind, message.ifBlank { kind.label }),
                                ProblemReport.body(context, message, includeDevice, if (includeCrash) crash else null),
                                log
                            )
                            if (sent) {
                                if (crash != null) CrashCatcher.clear(context)
                            } else {
                                noEmailApp = true
                                snackbar.showSnackbar("No email app found. Copy the report, or use GitHub instead.")
                            }
                        }
                    },
                    enabled = message.trim().length >= 5,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                ) {
                    Text("Send with my email app instead")
                }
                if (noEmailApp) {
                    OutlinedButton(
                        onClick = {
                            ProblemReport.copyToClipboard(
                                context,
                                ProblemReport.subject(kind, message.ifBlank { kind.label }),
                                ProblemReport.body(context, message, includeDevice, if (includeCrash) crash else null)
                            )
                            if (crash != null) CrashCatcher.clear(context)
                            scope.launch { snackbar.showSnackbar("Report copied. Paste it into an email to ${ProblemReport.EMAIL}.") }
                        },
                        enabled = message.trim().length >= 5,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.S))
                        Text("Copy report")
                    }
                }
                OutlinedButton(
                    onClick = { ProblemReport.openIssues(context, ProblemReport.issueUrl(kind, message, includeDevice, context)) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                ) {
                    Text("Report on GitHub instead")
                }
                Text(
                    "GitHub issues are public. Crash details and logs are never added there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.XS)
                )
            }
        }
    }
}

@Composable
private fun Check(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox)
            .padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.size(Spacing.M))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
