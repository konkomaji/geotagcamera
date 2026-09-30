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
        assertTrue(ProofVerifier.verify(base, payload, null))
    }

    /** Signed before the proof was embedded; embedding it must still verify (canonical hash is metadata-blind). */
    @Test
    fun stillVerifiesAfterProofIsEmbedded() {
        val base = TestJpeg.minimal()
        val payload = payloadFor(base)
        val withProof = XmpWriter.embed(base, "{\"proof\":\"whatever\"}")
        assertTrue(ProofVerifier.verify(withProof, payload, null))
    }

    @Test
    fun rejectsEditedImage() {
        val base = TestJpeg.minimal(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val payload = payloadFor(base)
        val edited = TestJpeg.minimal(byteArrayOf(0x11, 0x22, 0x33, 0x45))
        assertFalse(ProofVerifier.verify(edited, payload, null))
    }

    @Test
    fun malformedProofIsFalseNotCrash() {
        val bad = SignedPayload(1, "SHA256withECDSA", "deadbeef", "AA==", "not-a-real-base64-key!!!", "")
        assertFalse(ProofVerifier.verify(TestJpeg.minimal(), bad, null))
    }

    private val claims = ProofClaims(12.971599, 77.594566, 1_700_000_000_123L)
    private val goodMeta = ObservedMeta(12.971599, 77.594566, 1_700_000_000L)

    private fun v2Payload(jpeg: ByteArray, c: ProofClaims = claims): SignedPayload {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val hashHex = JpegCanonical.sha256Hex(jpeg)
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(kp.private); update(c.signedMessage(hashHex)); sign()
        }
        return SignedPayload(
            version = 2, alg = "SHA256withECDSA", sha256Hex = hashHex,
            signatureBase64 = Base64.getEncoder().encodeToString(sig),
            publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded),
            timestampIso = "", claims = c
        )
    }

    @Test
    fun v2VerifiesWhenExifMatchesClaims() {
        val jpeg = TestJpeg.minimal()
        assertTrue(ProofVerifier.verify(jpeg, v2Payload(jpeg), goodMeta))
    }

    @Test
    fun v2RejectsRewrittenGps() {
        val jpeg = TestJpeg.minimal()
        val moved = ObservedMeta(28.6139, 77.2090, 1_700_000_000L)
        assertFalse(ProofVerifier.verify(jpeg, v2Payload(jpeg), moved))
    }

    @Test
    fun v2RejectsRewrittenTime() {
        val jpeg = TestJpeg.minimal()
        val later = ObservedMeta(12.971599, 77.594566, 1_700_086_400L)
        assertFalse(ProofVerifier.verify(jpeg, v2Payload(jpeg), later))
    }

    @Test
    fun v2FailsClosedWhenExifMissing() {
        val jpeg = TestJpeg.minimal()
        assertFalse(ProofVerifier.verify(jpeg, v2Payload(jpeg), null))
        assertFalse(ProofVerifier.verify(jpeg, v2Payload(jpeg), ObservedMeta(null, null, null)))
    }

    /** Editing the claims inside the proof itself (to match doctored EXIF) breaks the signature. */
    @Test
    fun v2RejectsTamperedClaimsInPayload() {
        val jpeg = TestJpeg.minimal()
        val forged = v2Payload(jpeg).copy(claims = ProofClaims(28.6139, 77.2090, claims.epochMs))
        assertFalse(ProofVerifier.verify(jpeg, forged, ObservedMeta(28.6139, 77.2090, 1_700_000_000L)))
    }

    /** Stripping the claims and relabelling as v1 must not bypass the claim-bound signature. */
    @Test
    fun v2SignatureDoesNotVerifyAsV1() {
        val jpeg = TestJpeg.minimal()
        val downgraded = v2Payload(jpeg).copy(version = 1, claims = null)
        assertFalse(ProofVerifier.verify(jpeg, downgraded, null))
    }
}
