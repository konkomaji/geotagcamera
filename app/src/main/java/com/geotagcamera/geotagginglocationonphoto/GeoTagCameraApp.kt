package com.geotagcamera.geotagginglocationonphoto

import android.app.Application
import com.geotagcamera.geotagginglocationonphoto.location.map.OfflineMaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeoTagCameraApp : Application() {

    /** Lives as long as the process; for work that must outlast any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        com.geotagcamera.geotagginglocationonphoto.support.AppLog.init(this)
        // Google Play build only, and only after consent; a no-op in the foss build.
        com.geotagcamera.geotagginglocationonphoto.analytics.Analytics.init(this)
        com.geotagcamera.geotagginglocationonphoto.support.CrashCatcher.install(this)
        // Native crashes and freezes from the last run (Android 11+), kept for the crash offer.
        appScope.launch(Dispatchers.IO) { runCatching { com.geotagcamera.geotagginglocationonphoto.support.CrashCatcher.collectSystemExits(this@GeoTagCameraApp) } }
        // A shot is held in cache only until it is saved; one left by a killed process
        // still has GPS in it and is never coming back, so remove it.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
                cacheDir.listFiles { f -> f.name.startsWith("capture_") && f.lastModified() < cutoff }?.forEach { it.delete() }
            }
        }
        // Picks up offline map downloads that were cut off when the app last closed.
        appScope.launch { runCatching { OfflineMaps.load(this@GeoTagCameraApp) } }
    }
}
