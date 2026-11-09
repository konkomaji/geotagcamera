package com.geotagcamera.geotagginglocationonphoto.security

import com.geotagcamera.geotagginglocationonphoto.support.AppLog
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.geotagcamera.geotagginglocationonphoto.exif.JpegCanonical
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

data class IntegrityResult(
    val sha256Hex: String,
    val signatureBase64: String,
    val keyAlias: String,
    /**
     * The X.509 SubjectPublicKeyInfo (base64) for the signing key. The private
     * key is non-exportable from the Keystore by design, but the *public* key
     * can and must travel with the photo — it's what makes verification
     * portable to any device via [ProofVerifier], not just the one that shot it.
     */
    val publicKeyBase64: String,
    /** Location/time committed to by the signature (proof v2); null only for legacy v1 results. */
    val claims: ProofClaims? = null
)

/**
 * Tamper-evidence: hash the final stamped JPEG, sign that hash with a
 * per-device Android Keystore EC key whose private material never leaves
 * secure hardware, and hand back hash + signature + public key. The signed
 * message also commits to [ProofClaims] (lat/lon/time), because the hash alone
 * does not cover EXIF. The proof is
 * then embedded in the file (EXIF UserComment + XMP) so anyone can later
 * recompute the hash and check the signature — see docs/features.md.
 *
 * The hash is [JpegCanonical.canonicalDigest], NOT a raw whole-file digest:
 * the proof is written back into the file's own metadata after signing, so the
 * hash must be blind to metadata or it would invalidate itself. See
 * [JpegCanonical] for why that's correct rather than a loophole.
 */
object PhotoIntegrity {
    private const val KEY_ALIAS = "geotagcamera_signing_key"
    private const val KEYSTORE = "AndroidKeyStore"

    /**
     * Signs [file]. If the Keystore key is unusable (invalidated after a
     * security update or lock-screen change, or a broken Keystore), it is
     * replaced once and signing retried. Photos signed earlier still verify:
     * their proof carries the public key they were signed with. Throws only
     * when the Keystore can't sign at all; the caller then saves the photo
     * unsigned rather than losing it.
     */
    fun sign(file: File, claims: ProofClaims): IntegrityResult {
        val hash = JpegCanonical.canonicalDigest(file.readBytes())
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        val signatureBytes = try {
            signWith(getOrCreateKeyPair(), claims, hashHex)
        } catch (e: Exception) {
            if (e is java.io.IOException) throw e
            AppLog.w("Integrity", "signing key unusable, replacing it", e)
            runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
            signWith(getOrCreateKeyPair(), claims, hashHex)
        }
        return IntegrityResult(
            sha256Hex = hashHex,
            signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes),
            keyAlias = KEY_ALIAS,
            publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyEncoded()),
            claims = claims
        )
    }

    /** The canonical hash alone, for a photo that has to be saved unsigned. */
    fun hashOnly(file: File): String =
        JpegCanonical.canonicalDigest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun signWith(privateKey: PrivateKey, claims: ProofClaims, hashHex: String): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(claims.signedMessage(hashHex))
            sign()
        }

    /**
     * This device's signing public key (base64 SPKI), or null if this install
     * has never signed a photo. Lets the UI say "signed on this device" when a
     * photo's embedded key matches, which is stronger than "the file agrees
     * with the key it carries".
     */
    fun devicePublicKeyBase64OrNull(): String? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        keyStore.getCertificate(KEY_ALIAS)?.publicKey?.encoded?.let { Base64.getEncoder().encodeToString(it) }
    }.getOrNull()

    /** X.509 SubjectPublicKeyInfo bytes for the signing key (from the Keystore self-cert). */
    private fun publicKeyEncoded(): ByteArray {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getCertificate(KEY_ALIAS)?.publicKey?.encoded
            ?: throw java.security.KeyStoreException("signing key has no certificate")
    }

    private fun getOrCreateKeyPair(): PrivateKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? PrivateKey)?.let { return it }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair().private
    }
}
