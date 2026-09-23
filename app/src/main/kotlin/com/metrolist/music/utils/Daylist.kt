package com.metrolist.music.utils

import androidx.annotation.StringRes
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import java.time.LocalTime

/** Parts of the day the time-of-day playlist is built for, with their local hours. */
enum class DayPart(
    val hours: List<Int>,
    @StringRes val titleRes: Int,
) {
    MORNING((5..10).toList(), R.string.daylist_morning),
    DAY((11..16).toList(), R.string.daylist_day),
    EVENING((17..21).toList(), R.string.daylist_evening),
    NIGHT(listOf(22, 23, 0, 1, 2, 3, 4), R.string.daylist_night),
    ;

    companion object {
        fun of(hour: Int): DayPart = entries.first { hour in it.hours }

        fun now(): DayPart = of(LocalTime.now().hour)
    }
}

data class Daylist(
    val part: DayPart,
    val songs: List<Song>,
)
