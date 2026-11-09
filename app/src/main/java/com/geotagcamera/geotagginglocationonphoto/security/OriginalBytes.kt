package com.geotagcamera.geotagginglocationonphoto.security

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Reads a photo's bytes exactly as stored, GPS included.
 *
 * Since Android 10 the media store hands apps a copy with the GPS EXIF
 * removed unless the app holds ACCESS_MEDIA_LOCATION and asks for the
 * original. A redacted copy still verifies its pixels but can never pass the
 * signed location check, which is what "location not checked" meant for
 * photos picked from the gallery. Picker URIs from the system photo picker
 * are always redacted, so Verify uses the document picker instead.
 */
object OriginalBytes {

    fun read(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val original = originalUri(context, uri)
        if (original != null) {
            runCatching { resolver.openInputStream(original)?.use { return it.readCapped() } }
        }
        return runCatching { resolver.openInputStream(uri)?.use { it.readCapped() } }.getOrNull()
    }

    /** No real photo is this big; a provider that never stops sending must not exhaust memory. */
    private const val MAX_BYTES = 64 * 1024 * 1024

    private fun java.io.InputStream.readCapped(): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > MAX_BYTES) return null
            out.write(buf, 0, n)
        }
    }

    private fun originalUri(context: Context, uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val mediaUri = when (uri.authority) {
            MediaStore.AUTHORITY -> uri
            // Document picker "Images" root: map back to the media store item.
            "com.android.providers.media.documents" -> runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
            else -> null
        } ?: return null
        return runCatching { MediaStore.setRequireOriginal(mediaUri) }.getOrNull()
    }
}
