package com.geotagcamera.geotagginglocationonphoto.security

import com.geotagcamera.geotagginglocationonphoto.exif.JpegCanonical
import com.geotagcamera.geotagginglocationonphoto.exif.SignedPayload
import java.security.GeneralSecurityException
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
 * mismatch, never a crash.
 *
 * A failure of the check itself (out of memory, an unexpected runtime error)
 * is [Check.ERROR], not a mismatch: the caller must say "couldn't check",
 * never "edited". Calling a genuine photo tampered is the worst wrong answer
 * a verifier can give.
 */
object ProofVerifier {

    enum class Check { MATCH, MISMATCH, ERROR }

    /** True iff [jpeg]'s canonical hash matches the proof AND the signature verifies under the proof's public key (and, for v2, [observed] EXIF matches the signed claims). */
    fun verify(jpeg: ByteArray, payload: SignedPayload, observed: ObservedMeta?): Boolean =
        check(jpeg, payload, observed) == Check.MATCH

    fun check(jpeg: ByteArray, payload: SignedPayload, observed: ObservedMeta?): Check = try {
        if (verifyOrThrow(jpeg, payload, observed)) Check.MATCH else Check.MISMATCH
    } catch (e: GeneralSecurityException) {
        Check.MISMATCH // key or signature doesn't parse: the proof is not valid
    } catch (e: IllegalArgumentException) {
        Check.MISMATCH // bad base64 in the proof
    } catch (e: OutOfMemoryError) {
        Check.ERROR
    } catch (e: Exception) {
        Check.ERROR
    }

    private fun verifyOrThrow(jpeg: ByteArray, payload: SignedPayload, observed: ObservedMeta?): Boolean {
        val hash = JpegCanonical.canonicalDigest(jpeg)
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        if (!hashHex.equals(payload.sha256Hex, ignoreCase = true)) return false

        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(payload.publicKeyBase64)))

        val claims = payload.claims
        // A v2+ proof whose claims are missing/corrupt must not fall back to the hash-only check.
        if (payload.version >= 2 && claims == null) return false
        // v2 proofs also require the file's EXIF GPS/time to match what was signed;
        // a missing or rewritten EXIF fails closed.
        if (claims != null && (observed == null || !observed.matches(claims))) return false

        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(claims?.signedMessage(hashHex) ?: hash)
            verify(Base64.getDecoder().decode(payload.signatureBase64))
        }
    }
}
