package com.metrolist.music.viewmodels

import com.metrolist.music.db.entities.Event
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class YouViewModelTest {
    private val monday = LocalDateTime.of(2026, 9, 21, 0, 0)

    private fun event(at: LocalDateTime, ms: Long) = Event(songId = "s", timestamp = at, playTime = ms)

    @Test
    fun `listening is split by weekday and the week before is summed`() {
        val summary =
            summarizeWeek(
                listOf(
                    event(monday.plusHours(9), 1_000),
                    event(monday.plusHours(20), 500),
                    event(monday.plusDays(6).plusHours(23), 2_000),
                    event(monday.minusDays(1), 700),
                    event(monday.minusDays(7), 300),
                    event(monday.minusDays(8), 9_999),
                ),
                monday,
                topArtist = null,
            )

        assertEquals(listOf(1_500L, 0, 0, 0, 0, 0, 2_000L), summary.perDayMs)
        assertEquals(3_500L, summary.totalMs)
        assertEquals(1_000L, summary.previousWeekMs)
    }

    @Test
    fun `no events gives an empty week`() {
        val summary = summarizeWeek(emptyList(), monday, topArtist = null)

        assertEquals(List(7) { 0L }, summary.perDayMs)
        assertEquals(0L, summary.previousWeekMs)
    }
}
