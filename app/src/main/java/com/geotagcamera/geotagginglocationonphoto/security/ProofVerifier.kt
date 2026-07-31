package com.geotagcamera.geotagginglocationonphoto.security

import com.geotagcamera.geotagginglocationonphoto.exif.JpegCanonical
import com.geotagcamera.geotagginglocationonphoto.exif.SignedPayload
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Portable verification: given a photo's bytes and the [SignedPayload] pulled
 * from its embedded proof (EXIF UserComment or XMP), confirm the image hasn't
 * changed since capture — using ONLY the public key carried in the proof, no
 * Android Keystore. That's what lets any install verify any file, from any
 * source, offline, regardless of which device shot it.
 *
 * Pure JCE (`KeyFactory("EC")` + `SHA256withECDSA`), no Android dependency, so
 * it is unit-testable directly. Any malformed input — bad base64, a public key
 * that isn't a valid EC SPKI, a signature that doesn't parse — is a clean
 * `false`, never a crash.
 */
object ProofVerifier {

    /** True iff [jpeg]'s canonical hash matches the proof AND the signature verifies under the proof's public key. */
    fun verify(jpeg: ByteArray, payload: SignedPayload): Boolean = runCatching {
        val hash = JpegCanonical.canonicalDigest(jpeg)
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        if (!hashHex.equals(payload.sha256Hex, ignoreCase = true)) return false

        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(payload.publicKeyBase64)))

        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(hash)
            verify(Base64.getDecoder().decode(payload.signatureBase64))
        }
    }.getOrDefault(false)
}
