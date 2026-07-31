package com.geotagcamera.geotagginglocationonphoto.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.geotagcamera.geotagginglocationonphoto.exif.JpegCanonical
import java.io.File
import java.io.InputStream
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
    val publicKeyBase64: String
)

/**
 * Tamper-evidence: hash the final stamped JPEG, sign that hash with a
 * per-device Android Keystore EC key whose private material never leaves
 * secure hardware, and hand back hash + signature + public key. The proof is
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

    fun sign(file: File): IntegrityResult {
        val hash = JpegCanonical.canonicalDigest(file.readBytes())
        val privateKey = getOrCreateKeyPair()
        val signatureBytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(hash)
            sign()
        }
        return IntegrityResult(
            sha256Hex = hash.joinToString("") { "%02x".format(it) },
            signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes),
            keyAlias = KEY_ALIAS,
            publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyEncoded())
        )
    }

    /** Recomputes the file's canonical hash now and checks it against [expectedSha256Hex] and the local key's signature. */
    fun verify(file: File, expectedSha256Hex: String, signatureBase64: String): Boolean =
        file.inputStream().use { verify(it, expectedSha256Hex, signatureBase64) }

    /** Same check, but reading through a content:// Uri — how gallery photos are reached post-capture. */
    fun verify(context: Context, uri: Uri, expectedSha256Hex: String, signatureBase64: String): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        return input.use { verify(it, expectedSha256Hex, signatureBase64) }
    }

    private fun verify(input: InputStream, expectedSha256Hex: String, signatureBase64: String): Boolean {
        val hash = JpegCanonical.canonicalDigest(input.readBytes())
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        if (hashHex != expectedSha256Hex) return false

        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return false
        val signatureBytes = Base64.getDecoder().decode(signatureBase64)

        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(hash)
            verify(signatureBytes)
        }
    }

    /** X.509 SubjectPublicKeyInfo bytes for the signing key (from the Keystore self-cert). */
    private fun publicKeyEncoded(): ByteArray {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getCertificate(KEY_ALIAS).publicKey.encoded
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
