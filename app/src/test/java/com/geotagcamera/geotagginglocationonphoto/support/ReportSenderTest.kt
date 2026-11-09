package com.geotagcamera.geotagginglocationonphoto.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ReportSenderTest {
    @Test fun proofOfWorkMeetsTheTarget() {
        val content = ReportSender.sha256Hex("{\"message\":\"hello\"}")
        val pow = ReportSender.proofOfWork(1_800_000_000L, content, bits = 12)
        val (ts, nonce) = pow.split(":")
        assertEquals("1800000000", ts)
        val h = MessageDigest.getInstance("SHA-256").digest("geotag-report-v1:$ts:$content:$nonce".toByteArray())
        assertTrue(ReportSender.hasLeadingZeroBits(h, 12))
    }

    @Test fun leadingZeroBits() {
        assertTrue(ReportSender.hasLeadingZeroBits(byteArrayOf(0, 0x0F, 1), 12))
        assertFalse(ReportSender.hasLeadingZeroBits(byteArrayOf(0, 0x1F, 1), 12))
        assertTrue(ReportSender.hasLeadingZeroBits(byteArrayOf(0, 0, 0x7F), 17))
        assertFalse(ReportSender.hasLeadingZeroBits(byteArrayOf(0, 0, -128), 17))
    }
}
