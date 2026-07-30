package com.geotagcamera.geotagginglocationonphoto.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Publishes a fully processed (stamped, EXIF-tagged, signed) JPEG into the
 * device's real Photos/Gallery app under Pictures/GeoTagCamera, instead of
 * the app-private sandbox nobody but this app can see.
 *
 * Scoped storage (API 29+) needs no permission — insert + IS_PENDING write.
 * API 26-28 predates scoped storage and RELATIVE_PATH, so it writes the file
 * directly into the public Pictures dir (needs WRITE_EXTERNAL_STORAGE,
 * declared maxSdkVersion=28) and registers it via the legacy DATA column.
 */
object MediaStoreImageSaver {
    private const val RELATIVE_DIR = "GeoTagCamera"

    fun save(context: Context, sourceFile: File, displayName: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, sourceFile, displayName)
        } else {
            saveLegacy(context, sourceFile, displayName)
        }

    private fun saveScoped(context: Context, sourceFile: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$RELATIVE_DIR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        val wrote = runCatching {
            resolver.openOutputStream(uri)?.use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
                ?: error("no output stream")
        }.isSuccess

        if (!wrote) {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, sourceFile: File, displayName: String): Uri? {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            RELATIVE_DIR
        )
        if (!picturesDir.exists() && !picturesDir.mkdirs()) return null

        val destFile = File(picturesDir, displayName)
        val copied = runCatching { sourceFile.copyTo(destFile, overwrite = true) }.isSuccess
        if (!copied) return null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, destFile.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }
}
