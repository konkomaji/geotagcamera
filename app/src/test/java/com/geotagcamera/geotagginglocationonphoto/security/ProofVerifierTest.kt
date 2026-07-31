package com.geotagcamera.geotagginglocationonphoto.security

import com.geotagcamera.geotagginglocationonphoto.TestJpeg
import com.geotagcamera.geotagginglocationonphoto.exif.JpegCanonical
import com.geotagcamera.geotagginglocationonphoto.exif.SignedPayload
import com.geotagcamera.geotagginglocationonphoto.exif.XmpWriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class ProofVerifierTest {

    private fun payloadFor(jpeg: ByteArray): SignedPayload {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val hash = JpegCanonical.canonicalDigest(jpeg)
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(kp.private); update(hash); sign()
        }
        return SignedPayload(
            version = 1,
            alg = "SHA256withECDSA",
            sha256Hex = hash.joinToString("") { "%02x".format(it) },
            signatureBase64 = Base64.getEncoder().encodeToString(sig),
            publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded),
            timestampIso = ""
        )
    }

    @Test
    fun verifiesWithOnlyTheEmbeddedPublicKey() {
        val base = TestJpeg.minimal()
        val payload = payloadFor(base)
        assertTrue(ProofVerifier.verify(base, payload))
    }

    /** Signed before the proof was embedded; embedding it must still verify (canonical hash is metadata-blind). */
    @Test
    fun stillVerifiesAfterProofIsEmbedded() {
        val base = TestJpeg.minimal()
        val payload = payloadFor(base)
        val withProof = XmpWriter.embed(base, "{\"proof\":\"whatever\"}")
        assertTrue(ProofVerifier.verify(withProof, payload))
    }

    @Test
    fun rejectsEditedImage() {
        val base = TestJpeg.minimal(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val payload = payloadFor(base)
        val edited = TestJpeg.minimal(byteArrayOf(0x11, 0x22, 0x33, 0x45))
        assertFalse(ProofVerifier.verify(edited, payload))
    }

    @Test
    fun malformedProofIsFalseNotCrash() {
        val bad = SignedPayload(1, "SHA256withECDSA", "deadbeef", "AA==", "not-a-real-base64-key!!!", "")
        assertFalse(ProofVerifier.verify(TestJpeg.minimal(), bad))
    }
}
