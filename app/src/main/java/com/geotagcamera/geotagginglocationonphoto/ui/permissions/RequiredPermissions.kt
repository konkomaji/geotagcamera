package com.geotagcamera.geotagginglocationonphoto.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * WRITE_EXTERNAL_STORAGE is only needed pre-scoped-storage (API 26-28) to
 * write the JPEG directly into the public Pictures dir — see
 * MediaStoreImageSaver. API 29+ publishes via MediaStore + IS_PENDING and
 * needs no storage permission at all.
 */
val CAPTURE_PERMISSIONS: Array<String>
    get() = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

fun hasCapturePermissions(context: Context): Boolean = CAPTURE_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
