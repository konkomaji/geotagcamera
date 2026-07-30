package com.geotagcamera.geotagginglocationonphoto.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import android.util.Base64

data class IntegrityResult(
    val sha256Hex: String,
    val signatureBase64: String,
    val keyAlias: String
)

/**
 * Tamper-evidence: hash the final stamped JPEG, sign that hash with a
 * per-device Android Keystore EC key whose private key material never
 * leaves secure hardware, and store both. Anyone can later recompute the
 * file's hash and check it against the stored, signed value to prove the
 * bytes haven't changed since capture — that's the "provably unedited"
 * guarantee described in docs/features.md.
 */
object PhotoIntegrity {
    private const val KEY_ALIAS = "geotagcamera_signing_key"
    private const val KEYSTORE = "AndroidKeyStore"

    fun sign(file: File): IntegrityResult {
        val hash = sha256(file)
        val privateKey = getOrCreateKeyPair()
        val signatureBytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(hash)
            sign()
        }
        return IntegrityResult(
            sha256Hex = hash.joinToString("") { "%02x".format(it) },
            signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP),
            keyAlias = KEY_ALIAS
        )
    }

    /** Recomputes the file's hash now and checks it still matches [expectedSha256Hex] and the stored signature. */
    fun verify(file: File, expectedSha256Hex: String, signatureBase64: String): Boolean =
        file.inputStream().use { verify(it, expectedSha256Hex, signatureBase64) }

    /** Same check, but reading through a content:// Uri — how gallery photos are reached post-capture. */
    fun verify(context: Context, uri: Uri, expectedSha256Hex: String, signatureBase64: String): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        return input.use { verify(it, expectedSha256Hex, signatureBase64) }
    }

    private fun verify(input: InputStream, expectedSha256Hex: String, signatureBase64: String): Boolean {
        val hash = sha256(input)
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        if (hashHex != expectedSha256Hex) return false

        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return false
        val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)

        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(hash)
            verify(signatureBytes)
        }
    }

    private fun sha256(file: File): ByteArray = file.inputStream().use { sha256(it) }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    private fun getOrCreateKeyPair(): java.security.PrivateKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey)?.let { return it }

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
