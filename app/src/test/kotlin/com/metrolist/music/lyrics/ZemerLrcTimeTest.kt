package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class ZemerLrcTimeTest {
    @Test
    fun `formats minutes, seconds and hundredths`() {
        assertEquals("00:05.23", lrcTime(5.234))
        assertEquals("02:03.50", lrcTime(123.5))
    }

    @Test
    fun `rounding never produces sixty seconds`() {
        assertEquals("01:00.00", lrcTime(59.996))
        assertEquals("02:00.00", lrcTime(119.999))
    }
}
