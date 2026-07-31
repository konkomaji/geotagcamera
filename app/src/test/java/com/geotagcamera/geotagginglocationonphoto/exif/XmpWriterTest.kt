package com.geotagcamera.geotagginglocationonphoto.exif

import com.geotagcamera.geotagginglocationonphoto.TestJpeg
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpWriterTest {

    @Test
    fun embedThenExtractRoundTrips() {
        val proof = "{\"v\":1,\"h\":\"deadbeef\",\"sig\":\"AA==\",\"pk\":\"BB==\"}"
        val withXmp = XmpWriter.embed(TestJpeg.minimal(), proof)
        assertEquals(proof, XmpWriter.extract(withXmp))
    }

    @Test
    fun preservesSoiAndGrowsFile() {
        val base = TestJpeg.minimal()
        val withXmp = XmpWriter.embed(base, "{}")
        assertEquals(0xFF, withXmp[0].toInt() and 0xFF)
        assertEquals(0xD8, withXmp[1].toInt() and 0xFF)
        assertTrue(withXmp.size > base.size)
    }

    /** JSON's own quotes/braces plus XML-hostile chars must survive escaping. */
    @Test
    fun escapesXmlHostileCharacters() {
        val proof = "{\"note\":\"<tag> & \\\"quote\\\" 'apos'\"}"
        val withXmp = XmpWriter.embed(TestJpeg.minimal(), proof)
        assertEquals(proof, XmpWriter.extract(withXmp))
    }

    @Test
    fun nonJpegIsLeftUntouched() {
        val notJpeg = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertArrayEquals(notJpeg, XmpWriter.embed(notJpeg, "x"))
        assertNull(XmpWriter.extract(notJpeg))
    }

    @Test
    fun extractReturnsNullWhenNoXmp() {
        assertNull(XmpWriter.extract(TestJpeg.minimal()))
    }
}
