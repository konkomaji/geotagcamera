package com.geotagcamera.geotagginglocationonphoto.ui.settings

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import com.geotagcamera.geotagginglocationonphoto.util.safeStateIn
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.FileOutputStream
import com.geotagcamera.geotagginglocationonphoto.profile.UserProfile
import com.geotagcamera.geotagginglocationonphoto.ui.onboarding.OnboardingPreferences
import kotlinx.coroutines.flow.map

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val stampPreferences = StampPreferences(application)
    private val onboardingPreferences = OnboardingPreferences(application)

    /** People who onboarded before profiles existed are treated as Field. */
    val profile: StateFlow<UserProfile> = onboardingPreferences.profile
        .map { it ?: UserProfile.FIELD }
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile.FIELD)

    /** Switching profile also applies its recommended stamp defaults. */
    fun setProfile(profile: UserProfile) {
        viewModelScope.launchSafely(tag = "Settings") {
            onboardingPreferences.setProfile(profile)
            stampPreferences.edit { profile.applyDefaults(it) }
        }
    }

    val fields: StateFlow<StampFields?> = stampPreferences.fields
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoDismissReview: StateFlow<Boolean> = stampPreferences.autoDismissReview
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Applied inside one DataStore transaction against the stored value, never a UI snapshot. */
    fun update(transform: (StampFields) -> StampFields) {
        viewModelScope.launchSafely(tag = "Settings") { stampPreferences.edit(transform) }
    }

    fun setAutoDismissReview(enabled: Boolean) {
        viewModelScope.launchSafely(tag = "Settings") { stampPreferences.setAutoDismissReview(enabled) }
    }

    /**
     * Copies the picked image into app storage, downscaled to at most
     * [MAX_LOGO_PX] on its long side, and stores that path rather than the
     * picker's transient content:// Uri. The logo is burned into every stamp,
     * so it must survive restarts without a persistable grant, and a 4000 px
     * source would otherwise cost ~64 MB on every decode.
     *
     * Each pick gets a new file name so the capture screen, which caches the
     * decoded logo by path, can never keep burning in the old one.
     */
    fun setOrgLogo(uri: Uri) {
        viewModelScope.launchSafely(tag = "Settings") {
            val context = getApplication<Application>()
            val path = withContext(Dispatchers.IO) {
                val bitmap = decodeDownsampled(uri) ?: return@withContext null
                val dir = File(context.filesDir, LOGO_DIR).apply { mkdirs() }
                val file = File(dir, "org_logo_${System.currentTimeMillis()}.png")
                val ok = runCatching {
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.getOrDefault(false)
                bitmap.recycle()
                if (ok) file.absolutePath else { file.delete(); null }
            } ?: return@launchSafely
            stampPreferences.edit { it.copy(orgLogoUri = path, showOrgLogo = true) }
            // Old logos go only after the new one is written and saved.
            withContext(Dispatchers.IO) {
                File(context.filesDir, LOGO_DIR).listFiles()?.filter { it.absolutePath != path }?.forEach { it.delete() }
                File(context.filesDir, "org_logo.png").delete()
            }
        }
    }

    fun clearOrgLogo() {
        viewModelScope.launchSafely(tag = "Settings") {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                File(context.filesDir, LOGO_DIR).deleteRecursively()
                // Pre-1.2 installs kept a single fixed-name file.
                File(context.filesDir, "org_logo.png").delete()
            }
            stampPreferences.clearOrgLogo()
        }
    }

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_LOGO_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null
        val longSide = maxOf(decoded.width, decoded.height)
        if (longSide <= MAX_LOGO_PX) return decoded
        val scale = MAX_LOGO_PX.toFloat() / longSide
        val scaled = Bitmap.createScaledBitmap(
            decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private companion object {
        const val MAX_LOGO_PX = 512
        const val LOGO_DIR = "org_logo"
    }
}
