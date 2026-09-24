package com.metrolist.music.ui.screens.wrapped

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WrappedPeriodTest {
    @Test
    fun `ranges cover whole days from the first to the last`() {
        assertEquals(WrappedRange(LocalDateTime.of(2026, 9, 18, 0, 0), NOW), WrappedPeriod.Last7Days.range(NOW))
        assertEquals(
            WrappedRange(LocalDateTime.of(2026, 2, 1, 0, 0), LocalDate.of(2026, 2, 28).atTime(LocalTime.MAX)),
            WrappedPeriod.InMonth(YearMonth.of(2026, 2)).range(NOW),
        )
        assertEquals(
            WrappedRange(LocalDateTime.of(2025, 1, 1, 0, 0), LocalDate.of(2025, 12, 31).atTime(LocalTime.MAX)),
            WrappedPeriod.InYear(2025).range(NOW),
        )
        assertEquals(
            WrappedRange(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDate.of(2026, 9, 15).atTime(LocalTime.MAX)),
            WrappedPeriod.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)).range(NOW),
        )
        assertEquals(NOW, WrappedPeriod.AllTime.range(NOW).to)
    }

    @Test
    fun `every period survives the navigation route`() {
        listOf(
            WrappedPeriod.Last7Days,
            WrappedPeriod.InMonth(YearMonth.of(2026, 8)),
            WrappedPeriod.InYear(2025),
            WrappedPeriod.AllTime,
            WrappedPeriod.Custom(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 5)),
        ).forEach { assertEquals(it, wrappedPeriodFromRoute(it.toRoute())) }
    }

    @Test
    fun `broken routes give no period`() {
        listOf(null, "", "x", "m2026-13", "yabc", "c2026-09-15_2026-09-01", "c2026-09-01").forEach {
            assertNull(it, wrappedPeriodFromRoute(it))
        }
    }

    @Test
    fun `picker offers recent periods and every year back to the first listen`() {
        assertEquals(
            listOf(
                WrappedPeriod.Last7Days,
                WrappedPeriod.InMonth(YearMonth.of(2026, 9)),
                WrappedPeriod.InMonth(YearMonth.of(2026, 8)),
                WrappedPeriod.InYear(2026),
                WrappedPeriod.InYear(2025),
                WrappedPeriod.InYear(2024),
                WrappedPeriod.AllTime,
            ),
            wrappedPeriodChoices(TODAY, firstListen = LocalDate.of(2024, 5, 1)),
        )
        assertEquals(
            listOf(
                WrappedPeriod.Last7Days,
                WrappedPeriod.InMonth(YearMonth.of(2027, 1)),
                WrappedPeriod.InMonth(YearMonth.of(2026, 12)),
                WrappedPeriod.InYear(2027),
                WrappedPeriod.AllTime,
            ),
            wrappedPeriodChoices(LocalDate.of(2027, 1, 10), firstListen = LocalDate.of(2027, 1, 2)),
        )
        assertEquals(listOf(WrappedPeriod.InYear(2026)), wrappedPeriodChoices(TODAY, firstListen = null).filterIsInstance<WrappedPeriod.InYear>())
    }

    @Test
    fun `home card offers the year that is ending or just ended`() {
        assertEquals(2026, wrappedSeasonYear(LocalDate.of(2026, 12, 1)))
        assertEquals(2026, wrappedSeasonYear(LocalDate.of(2027, 1, 31)))
        assertNull(wrappedSeasonYear(LocalDate.of(2027, 2, 1)))
        assertNull(wrappedSeasonYear(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `elapsed days count only the listened part of the period`() {
        assertEquals(208, WrappedPeriod.InYear(2026).elapsedDays(NOW, firstListen = LocalDateTime.of(2026, 3, 1, 9, 0)))
        assertEquals(365, WrappedPeriod.InYear(2025).elapsedDays(NOW, firstListen = LocalDateTime.of(2024, 3, 1, 9, 0)))
        assertEquals(7, WrappedPeriod.Last7Days.elapsedDays(NOW, firstListen = LocalDateTime.of(2020, 1, 1, 0, 0)))
        assertEquals(5, WrappedPeriod.AllTime.elapsedDays(NOW, firstListen = LocalDateTime.of(2026, 9, 20, 23, 0)))
        assertEquals(
            4,
            WrappedPeriod.Custom(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30)).elapsedDays(NOW, firstListen = null),
        )
    }

    @Test
    fun `period label is built from digits only`() {
        assertEquals(listOf("2025"), WrappedPeriod.InYear(2025).bigLabel(NOW, firstListen = null))
        assertEquals(listOf("09.2026"), WrappedPeriod.InMonth(YearMonth.of(2026, 9)).bigLabel(NOW, firstListen = null))
        assertEquals(listOf("18.09", "24.09"), WrappedPeriod.Last7Days.bigLabel(NOW, firstListen = null))
        assertEquals(listOf("2025", "2026"), WrappedPeriod.Last7Days.bigLabel(LocalDateTime.of(2026, 1, 3, 10, 0), firstListen = null))
        assertEquals(
            listOf("01.09", "15.09"),
            WrappedPeriod.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)).bigLabel(NOW, firstListen = null),
        )
        assertEquals(listOf("01.09"), WrappedPeriod.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)).bigLabel(NOW, firstListen = null))
        assertEquals(listOf("2023", "2026"), WrappedPeriod.AllTime.bigLabel(NOW, firstListen = LocalDateTime.of(2023, 6, 1, 0, 0)))
        assertEquals(listOf("2026"), WrappedPeriod.AllTime.bigLabel(NOW, firstListen = LocalDateTime.of(2026, 6, 1, 0, 0)))
    }

    @Test
    fun `message tier follows minutes per day, not the total`() {
        assertEquals(0, WrappedRepository.tier(totalMinutes = 0, days = 7))
        assertEquals(1, WrappedRepository.tier(totalMinutes = 1_000, days = 365))
        assertEquals(1, WrappedRepository.tier(totalMinutes = 20, days = 7))
        assertEquals(2, WrappedRepository.tier(totalMinutes = 100, days = 7))
        assertEquals(3, WrappedRepository.tier(totalMinutes = 300, days = 7))
        assertEquals(4, WrappedRepository.tier(totalMinutes = 800, days = 7))
        assertEquals(4, WrappedRepository.tier(totalMinutes = 200, days = 0))
    }

    @Test
    fun `messages fall back instead of failing on a bad index`() {
        repeat(20) { assertEquals(true, WrappedRepository.randomIndex(tier = 2) in 0 until 4) }
        assertEquals(WrappedRepository.message(tier = 9, index = 0), WrappedRepository.message(tier = 0, index = 9))
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 24, 15, 0)
        val TODAY: LocalDate = NOW.toLocalDate()
    }
}
