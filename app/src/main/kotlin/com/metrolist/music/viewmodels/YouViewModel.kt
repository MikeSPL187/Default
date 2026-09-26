package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Event
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** This week in numbers: listening per weekday, Monday first, and the week before for comparison. */
data class WeekSummary(
    val perDayMs: List<Long>,
    val previousWeekMs: Long,
    val topArtist: Artist?,
) {
    val totalMs get() = perDayMs.sum()
}

internal fun summarizeWeek(
    events: List<Event>,
    weekStart: LocalDateTime,
    topArtist: Artist?,
): WeekSummary {
    val perDay = LongArray(7)
    var previous = 0L
    events.forEach { event ->
        if (event.timestamp >= weekStart) {
            perDay[event.timestamp.dayOfWeek.value - 1] += event.playTime
        } else if (event.timestamp >= weekStart.minusWeeks(1)) {
            previous += event.playTime
        }
    }
    return WeekSummary(perDay.toList(), previous, topArtist)
}

@HiltViewModel
class YouViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay()

    val week: StateFlow<WeekSummary?> =
        combine(
            database.eventsSince(weekStart.minusWeeks(1)),
            database.mostPlayedArtists(weekStart, limit = 1),
        ) { events, artists -> summarizeWeek(events, weekStart, artists.firstOrNull()) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _account = MutableStateFlow<AccountInfo?>(null)

    /** The signed-in account, or null for a guest. */
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { !it[InnerTubeCookieKey].isNullOrEmpty() }
                .distinctUntilChanged()
                .collect { signedIn ->
                    _account.value = if (signedIn) YouTube.accountInfo().getOrNull() else null
                }
        }
    }
}
