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
        val claims = payload.claims
        // v1 proofs carry no claims, so skip the second EXIF parse entirely.
        val observed = if (claims != null) ProofReader.readObserved(bytes) else null
        if (ProofVerifier.verify(bytes, payload, observed)) return VerificationOutcome.Untampered(payload)
        // No GPS/time to compare at all: distinguish "metadata gone" from "metadata changed".
        if (claims != null && observed != null && !observed.hasAny() &&
            ProofVerifier.verify(bytes, payload, ObservedMeta(claims.latitude, claims.longitude, Math.floorDiv(claims.epochMs, 1000L)))
        ) return VerificationOutcome.MetadataUnavailable(payload)
        return VerificationOutcome.Edited(payload)
    }

    fun verifyUri(context: Context, uri: Uri): VerificationOutcome {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return VerificationOutcome.Unreadable
        return verify(bytes)
    }
}
