package com.geotagcamera.geotagginglocationonphoto.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * The one shared image chooser (design section 08). Uses `GetContent`, which
 * adds zero permissions by construction — the system picker/SAF hands back a
 * readable content:// Uri without READ_MEDIA_IMAGES. Reused by Verify (pick a
 * photo to check), the Gallery FAB (Phase 9), and the Settings org-logo upload
 * (Phase 10). Returns a launcher lambda; call it to open the picker.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onPicked)
    }
    return { launcher.launch("image/*") }
}
