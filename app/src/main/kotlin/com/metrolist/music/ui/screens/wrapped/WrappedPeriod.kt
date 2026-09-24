package com.metrolist.music.ui.screens.wrapped

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The stretch of listening history a recap ("Wrapped") covers. */
sealed interface WrappedPeriod {
    /** The last seven days, today included. */
    data object Last7Days : WrappedPeriod

    data class InMonth(val month: YearMonth) : WrappedPeriod

    data class InYear(val year: Int) : WrappedPeriod

    data object AllTime : WrappedPeriod

    /** Whole days, both ends included. */
    data class Custom(val start: LocalDate, val end: LocalDate) : WrappedPeriod
}

/** Events are counted when `from <= timestamp <= to`. */
data class WrappedRange(val from: LocalDateTime, val to: LocalDateTime)

/** Listening history is never older than this, so "all time" can start here. */
private val HISTORY_START: LocalDateTime = LocalDateTime.of(2000, 1, 1, 0, 0)

fun WrappedPeriod.range(now: LocalDateTime): WrappedRange =
    when (this) {
        WrappedPeriod.Last7Days -> WrappedRange(now.toLocalDate().minusDays(6).atStartOfDay(), now)
        is WrappedPeriod.InMonth -> WrappedRange(month.atDay(1).atStartOfDay(), month.atEndOfMonth().atTime(LocalTime.MAX))
        is WrappedPeriod.InYear -> WrappedRange(LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year, 12, 31).atTime(LocalTime.MAX))
        WrappedPeriod.AllTime -> WrappedRange(HISTORY_START, now)
        is WrappedPeriod.Custom -> WrappedRange(start.atStartOfDay(), end.atTime(LocalTime.MAX))
    }

/**
 * Days the period has actually lasted, for judging how much was listened per day: a month or
 * year still in progress counts only up to today, and "all time" starts at the first listen.
 */
fun WrappedPeriod.elapsedDays(now: LocalDateTime, firstListen: LocalDateTime?): Long {
    val range = range(now)
    val start = maxOf(range.from, firstListen ?: range.from).toLocalDate()
    val end = minOf(range.to, now).toLocalDate()
    return (ChronoUnit.DAYS.between(start, end) + 1).coerceAtLeast(1)
}

/**
 * The large decorative label on the intro page. The display font only has Latin glyphs and
 * digits, so it is built from numbers: "2025", "09.2026", "18.09–24.09" or "2023–2026".
 */
fun WrappedPeriod.bigLabel(now: LocalDateTime, firstListen: LocalDateTime?): String {
    fun days(start: LocalDate, end: LocalDate) =
        if (start.year == end.year) {
            "${start.format(DAY_MONTH)}–${end.format(DAY_MONTH)}"
        } else {
            years(start.year, end.year)
        }
    return when (this) {
        is WrappedPeriod.InYear -> year.toString()
        is WrappedPeriod.InMonth -> month.format(MONTH_YEAR)
        WrappedPeriod.Last7Days, is WrappedPeriod.Custom -> range(now).let { days(it.from.toLocalDate(), it.to.toLocalDate()) }
        WrappedPeriod.AllTime -> years((firstListen ?: now).year, now.year)
    }
}

private fun years(first: Int, last: Int) = if (first >= last) last.toString() else "$first–$last"

private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM")
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MM.yyyy")

const val WRAPPED_ROUTE = "wrapped"

/** Periods with less listening than this have nothing worth recapping. */
const val WRAPPED_MIN_PLAY_TIME_MS = 60_000L

fun wrappedRoute(period: WrappedPeriod): String = "$WRAPPED_ROUTE?period=${period.toRoute()}"

/** Navigation argument form, see [wrappedPeriodFromRoute]. */
fun WrappedPeriod.toRoute(): String =
    when (this) {
        WrappedPeriod.Last7Days -> "7d"
        is WrappedPeriod.InMonth -> "m$month"
        is WrappedPeriod.InYear -> "y$year"
        WrappedPeriod.AllTime -> "all"
        is WrappedPeriod.Custom -> "c${start}_$end"
    }

fun wrappedPeriodFromRoute(value: String?): WrappedPeriod? =
    runCatching {
        when {
            value.isNullOrBlank() -> null
            value == "7d" -> WrappedPeriod.Last7Days
            value == "all" -> WrappedPeriod.AllTime
            value.startsWith("m") -> WrappedPeriod.InMonth(YearMonth.parse(value.drop(1)))
            value.startsWith("y") -> WrappedPeriod.InYear(value.drop(1).toInt())
            value.startsWith("c") -> {
                val (start, end) = value.drop(1).split("_").map(LocalDate::parse)
                if (end.isBefore(start)) null else WrappedPeriod.Custom(start, end)
            }
            else -> null
        }
    }.getOrNull()

/**
 * The periods offered in the recap picker, newest first: the last 7 days, this and last month,
 * this year, every earlier year back to the first listen, and all time.
 */
fun wrappedPeriodChoices(today: LocalDate, firstListen: LocalDate?): List<WrappedPeriod> {
    val thisMonth = YearMonth.from(today)
    val firstYear = (firstListen ?: today).year.coerceAtMost(today.year)
    return buildList {
        add(WrappedPeriod.Last7Days)
        add(WrappedPeriod.InMonth(thisMonth))
        add(WrappedPeriod.InMonth(thisMonth.minusMonths(1)))
        for (year in today.year downTo firstYear) add(WrappedPeriod.InYear(year))
        add(WrappedPeriod.AllTime)
    }
}

/**
 * The year the home screen offers a recap for: in December the year that is ending, in January
 * the one that just ended. Null the rest of the year.
 */
fun wrappedSeasonYear(today: LocalDate): Int? =
    when (today.month) {
        Month.DECEMBER -> today.year
        Month.JANUARY -> today.year - 1
        else -> null
    }
