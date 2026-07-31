package com.geotagcamera.geotagginglocationonphoto.ui.legal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AboutLegalViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val stampPreferences = StampPreferences(application)

    /**
     * Clears the app's own data: capture index, geocode cache, tile cache, org
     * logo, and stamp preferences. Deliberately does NOT touch the saved photos
     * themselves — those live in the user's gallery (MediaStore) and are theirs
     * to keep or delete. The UI says so plainly.
     */
    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.photoDao().deleteAll()
                db.geoCacheDao().deleteAll()
                val context = getApplication<Application>()
                runCatching { File(context.filesDir, "org_logo.png").delete() }
                runCatching { File(context.cacheDir, "map_tiles").deleteRecursively() }
            }
            stampPreferences.clearAll()
            onDone()
        }
    }
}
