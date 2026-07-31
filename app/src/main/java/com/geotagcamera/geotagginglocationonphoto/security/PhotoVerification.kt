package com.geotagcamera.geotagginglocationonphoto.security

import android.content.Context
import android.net.Uri
import com.geotagcamera.geotagginglocationonphoto.exif.ProofReader

/**
 * One place that turns a photo into a [VerificationOutcome]: read the embedded
 * proof ([ProofReader]) and check it portably ([ProofVerifier]). Shared by the
 * Verify screen, the Gallery per-tile status pass, and Photo Detail so they can
 * never disagree about what "verified" means.
 */
object PhotoVerification {

    fun verify(bytes: ByteArray): VerificationOutcome {
        val payload = ProofReader.read(bytes) ?: return VerificationOutcome.NoProof
        return if (ProofVerifier.verify(bytes, payload)) VerificationOutcome.Untampered(payload)
        else VerificationOutcome.Edited(payload)
    }

    fun verifyUri(context: Context, uri: Uri): VerificationOutcome {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return VerificationOutcome.Unreadable
        return verify(bytes)
    }
}
