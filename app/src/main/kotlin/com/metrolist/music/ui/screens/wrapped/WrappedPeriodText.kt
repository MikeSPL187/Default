package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import android.text.format.DateUtils
import com.metrolist.music.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

// Dates go through DateUtils so month names, their grammatical case and range formats follow the
// app's language ("18–24 сент. 2026 г.", "Sep 18 – 24, 2026").

private fun LocalDate.millis(): Long = atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun monthName(context: Context, month: YearMonth): String =
    DateUtils.formatDateTime(
        context,
        month.atDay(1).millis(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR,
    )

private fun shortDate(context: Context, date: LocalDate, today: LocalDate): String =
    DateUtils.formatDateTime(
        context,
        date.millis(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
            if (date.year == today.year) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR,
    )

private fun dateRange(context: Context, start: LocalDate, end: LocalDate): String =
    DateUtils.formatDateRange(
        context,
        start.millis(),
        end.millis(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR,
    )

/** Ends sentences such as "Your top songs %s": "in the last 7 days", "in September 2026", "overall". */
fun WrappedPeriod.phrase(context: Context, today: LocalDate): String =
    when (this) {
        WrappedPeriod.Last7Days -> context.getString(R.string.wrapped_period_last_7_days)
        is WrappedPeriod.InMonth -> context.getString(R.string.wrapped_period_month, monthName(context, month))
        is WrappedPeriod.InYear -> context.getString(R.string.wrapped_period_year, year)
        WrappedPeriod.AllTime -> context.getString(R.string.wrapped_period_all_time)
        is WrappedPeriod.Custom ->
            if (start == end) {
                context.getString(R.string.wrapped_period_day, shortDate(context, start, today))
            } else {
                context.getString(R.string.wrapped_period_custom, shortDate(context, start, today), shortDate(context, end, today))
            }
    }

/** A standalone name for the period, as listed in the picker. */
fun WrappedPeriod.title(context: Context): String =
    when (this) {
        WrappedPeriod.Last7Days -> context.getString(R.string.wrapped_last_7_days)
        is WrappedPeriod.InMonth -> {
            val locale = context.resources.configuration.locales[0]
            monthName(context, month).replaceFirstChar { it.titlecase(locale) }
        }
        is WrappedPeriod.InYear -> year.toString()
        WrappedPeriod.AllTime -> context.getString(R.string.wrapped_all_time)
        is WrappedPeriod.Custom -> dateRange(context, start, end)
    }

/** Names the saved playlist; "the last 7 days" becomes its actual dates so the name stays true later. */
fun WrappedPeriod.playlistName(context: Context, now: LocalDateTime): String {
    val label =
        if (this == WrappedPeriod.Last7Days) {
            range(now).let { dateRange(context, it.from.toLocalDate(), it.to.toLocalDate()) }
        } else {
            title(context)
        }
    return "Metrolist · $label"
}
