package com.geotagcamera.geotagginglocationonphoto.stamp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class StampDateTimePatternTest {
    // 2026-10-14 21:36:53 UTC
    private val t = 1_792_013_813_000L

    private fun format(locale: Locale): String =
        SimpleDateFormat(stampDateTimePattern(locale), locale)
            .apply { timeZone = TimeZone.getTimeZone("Europe/Vienna") }
            .format(t)

    @Test fun germanUsesDotsAnd24Hours() {
        val s = format(Locale.GERMANY)
        assertTrue(s, s.startsWith("Mittwoch, 14.10.2026"))
        assertTrue(s, s.contains("23:36"))
        assertFalse(s, s.contains("PM"))
    }

    @Test fun usUsesMonthFirstAnd12Hours() {
        val s = format(Locale.US)
        assertTrue(s, s.startsWith("Wednesday, 10/14/2026"))
        assertTrue(s, s.contains("11:36"))
    }

    @Test fun yearIsAlwaysFourDigits() {
        for (l in listOf(Locale.US, Locale.UK, Locale.GERMANY, Locale.FRANCE, Locale.JAPAN, Locale("hi", "IN"), Locale("en", "IN"))) {
            assertTrue("$l: ${format(l)}", format(l).contains("2026"))
        }
    }

    @Test fun weekdayComesFirst() {
        assertEquals("EEEE, ", stampDateTimePattern(Locale.US).take(6))
    }
}
