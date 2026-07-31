package com.geotagcamera.geotagginglocationonphoto.exif

import com.geotagcamera.geotagginglocationonphoto.security.IntegrityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class UserCommentCodecTest {

    @Test
    fun encodeThenDecodeRoundTrips() {
        val result = IntegrityResult(
            sha256Hex = "deadbeef",
            signatureBase64 = "c2lnQjY0",
            keyAlias = "geotagcamera_signing_key",
            publicKeyBase64 = "cGtCNjQ="
        )
        val json = UserCommentCodec.encode(result, 1_700_000_000_000L)
        val payload = UserCommentCodec.decode(json)!!

        assertEquals(1, payload.version)
        assertEquals("SHA256withECDSA", payload.alg)
        assertEquals("deadbeef", payload.sha256Hex)
        assertEquals("c2lnQjY0", payload.signatureBase64)
        assertEquals("cGtCNjQ=", payload.publicKeyBase64)
        assertTrue(payload.timestampIso.endsWith("Z"))
    }

    @Test
    fun decodeRejectsGarbageAndNull() {
        assertNull(UserCommentCodec.decode(null))
        assertNull(UserCommentCodec.decode(""))
        assertNull(UserCommentCodec.decode("not json at all"))
        assertNull(UserCommentCodec.decode("{\"v\":1}")) // required fields missing
    }

    /** "Measure, don't assume": a realistic P-256 proof must fit EXIF UserComment comfortably. */
    @Test
    fun realisticPayloadStaysSmall() {
        val sig = Base64.getEncoder().encodeToString(ByteArray(72)) // DER ECDSA/P-256 ~70-72 bytes
        val pk = Base64.getEncoder().encodeToString(ByteArray(91))  // X.509 SPKI for P-256 ~91 bytes
        val hex = "a".repeat(64)                                    // SHA-256 hex
        val json = UserCommentCodec.encode(
            IntegrityResult(hex, sig, "geotagcamera_signing_key", pk),
            1_700_000_000_000L
        )
        val bytes = json.toByteArray(Charsets.UTF_8).size
        assertTrue("proof is $bytes bytes, expected < 512", bytes < 512)
    }
}
