package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DayPartTest {
    @Test
    fun `every hour belongs to exactly one part of the day`() {
        (0..23).forEach { hour ->
            assertEquals("hour $hour", 1, DayPart.entries.count { hour in it.hours })
        }
    }

    @Test
    fun `boundaries fall where the section titles say`() {
        assertEquals(DayPart.NIGHT, DayPart.of(4))
        assertEquals(DayPart.MORNING, DayPart.of(5))
        assertEquals(DayPart.DAY, DayPart.of(11))
        assertEquals(DayPart.EVENING, DayPart.of(17))
        assertEquals(DayPart.NIGHT, DayPart.of(22))
        assertEquals(DayPart.NIGHT, DayPart.of(0))
    }
}
