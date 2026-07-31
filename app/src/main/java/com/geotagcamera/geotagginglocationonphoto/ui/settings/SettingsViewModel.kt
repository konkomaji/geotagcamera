package com.geotagcamera.geotagginglocationonphoto.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val stampPreferences = StampPreferences(application)

    val fields: StateFlow<StampFields> = stampPreferences.fields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampFields())

    val autoDismissReview: StateFlow<Boolean> = stampPreferences.autoDismissReview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun update(transform: (StampFields) -> StampFields) {
        viewModelScope.launch {
            stampPreferences.update(transform(fields.value))
        }
    }

    fun setAutoDismissReview(enabled: Boolean) {
        viewModelScope.launch { stampPreferences.setAutoDismissReview(enabled) }
    }

    /**
     * Copies the picked image into app storage and stores that path, rather
     * than the picker's transient content:// Uri — the logo is burned into
     * every stamp, so it must survive restarts without any persistable-URI
     * grant (GetContent doesn't offer one).
     */
    fun setOrgLogo(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext null
                val file = File(context.filesDir, "org_logo.png")
                runCatching { file.writeBytes(bytes) }.getOrNull() ?: return@withContext null
                file.absolutePath
            } ?: return@launch
            stampPreferences.update(fields.value.copy(orgLogoUri = path))
        }
    }

    fun clearOrgLogo() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(getApplication<Application>().filesDir, "org_logo.png").delete() }
            }
            stampPreferences.clearOrgLogo()
        }
    }
}
