package com.geotagcamera.geotagginglocationonphoto.exif

import com.geotagcamera.geotagginglocationonphoto.TestJpeg
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JpegCanonicalTest {

    /** The whole scheme rests on this: embedding the proof (an APP1 segment) must not move the hash. */
    @Test
    fun metadataInsertionDoesNotChangeDigest() {
        val base = TestJpeg.minimal()
        val withXmp = XmpWriter.embed(base, "{\"h\":\"deadbeef\",\"sig\":\"AA==\"}")
        assertArrayEquals(
            JpegCanonical.canonicalDigest(base),
            JpegCanonical.canonicalDigest(withXmp)
        )
    }

    /** Two different APP1 payloads still hash identically — metadata is fully excluded. */
    @Test
    fun differentMetadataHashesTheSame() {
        val a = XmpWriter.embed(TestJpeg.minimal(), "{\"a\":1}")
        val b = XmpWriter.embed(TestJpeg.minimal(), "{\"totally\":\"different and longer proof payload\"}")
        assertArrayEquals(JpegCanonical.canonicalDigest(a), JpegCanonical.canonicalDigest(b))
    }

    /** A single changed byte of actual image (scan) data must change the hash. */
    @Test
    fun imageContentChangeChangesDigest() {
        val base = TestJpeg.minimal(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val edited = TestJpeg.minimal(byteArrayOf(0x11, 0x22, 0x33, 0x45))
        assertFalse(
            JpegCanonical.canonicalDigest(base).contentEquals(JpegCanonical.canonicalDigest(edited))
        )
    }
}
