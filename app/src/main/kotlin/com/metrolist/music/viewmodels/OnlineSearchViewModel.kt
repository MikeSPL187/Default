/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.read
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = SearchRoutes.decodeQuery(savedStateHandle.get<String>("query").orEmpty())
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    private suspend fun resolveSearchMetadata(items: List<YTItem>): List<YTItem> =
        coroutineScope {
            val knownDurations =
                items
                    .filterIsInstance<SongItem>()
                    .mapNotNull { song -> song.duration?.let { song.id to it } }
                    .toMap()
            val missingDurationIds =
                items
                    .filterIsInstance<SongItem>()
                    .filter { it.duration == null && it.id !in knownDurations }
                    .map { it.id }
                    .distinct()
            val resolvedArtists = async { YouTube.resolveArtistIds(items) }
            val fetchedDurations =
                async {
                    if (missingDurationIds.isEmpty()) {
                        emptyMap()
                    } else {
                        YouTube
                            .queue(videoIds = missingDurationIds)
                            .getOrDefault(emptyList())
                            .mapNotNull { song -> song.duration?.let { song.id to it } }
                            .toMap()
                    }
                }
            val durations = knownDurations + fetchedDurations.await()

            resolvedArtists.await().map { item ->
                if (item is SongItem && item.duration == null) {
                    item.copy(duration = durations[item.id])
                } else {
                    item
                }
            }
        }

    /** Set when the "All" summary could not be loaded; the UI offers a retry. */
    var summaryLoadFailed by mutableStateOf(false)
        private set

    /** Filters whose first page failed to load, keyed by filter value. */
    val filterLoadFailures = mutableStateMapOf<String, Boolean>()
    private var loadingMore = false

    private suspend fun hiddenContentFilter(items: List<YTItem>): List<YTItem> {
        val hideExplicit = context.dataStore.read(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
        return items
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .filterYoutubeShorts(hideYoutubeShorts)
    }

    private suspend fun loadSummaryPage(force: Boolean = false) {
        if (summaryPage != null && !force) return
        summaryLoadFailed = false
        YouTube
            .searchSummary(query)
            .onSuccess { page ->
                val resolvedItems = resolveSearchMetadata(page.summaries.flatMap { it.items })
                var offset = 0
                val resolvedSummaries =
                    page.summaries.map { summary ->
                        val nextOffset = offset + summary.items.size
                        val resolvedSummary = summary.copy(items = resolvedItems.subList(offset, nextOffset))
                        offset = nextOffset
                        resolvedSummary
                    }
                val resolvedPage = page.copy(summaries = resolvedSummaries)
                val hideExplicit = context.dataStore.read(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
                summaryPage =
                    resolvedPage
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
            }.onFailure {
                summaryLoadFailed = true
                reportException(it)
            }
    }

    /**
     * Items of the "All" summary that belong to [filterValue]. Filtered searches sometimes omit
     * results the summary shows (most visibly the top album), so they are merged in front.
     */
    private fun summaryItemsFor(filterValue: String): List<YTItem> =
        summaryPage?.summaries.orEmpty().flatMap { it.items }.filter { item ->
            when (filterValue) {
                YouTube.SearchFilter.FILTER_SONG.value -> item is SongItem && !item.isVideoSong
                YouTube.SearchFilter.FILTER_VIDEO.value -> item is SongItem && item.isVideoSong
                YouTube.SearchFilter.FILTER_ALBUM.value -> item is AlbumItem
                YouTube.SearchFilter.FILTER_ARTIST.value -> item is ArtistItem && !item.isProfile
                YouTube.SearchFilter.FILTER_PODCAST.value -> item is PodcastItem
                YouTube.SearchFilter.FILTER_EPISODE.value -> item is EpisodeItem || (item is SongItem && item.isEpisode)
                YouTube.SearchFilter.FILTER_PROFILE.value -> item is ArtistItem && item.isProfile
                else -> false
            }
        }

    private suspend fun loadFilteredPage(filter: YouTube.SearchFilter, force: Boolean = false) {
        val filterValue = filter.value
        if (!force && viewStateMap[filterValue] != null) return
        if (force) viewStateMap.remove(filterValue)
        filterLoadFailures[filterValue] = false

        if (filter == YouTube.SearchFilter.FILTER_EPISODE) {
            // The FILTER_EPISODE API returns episodes in a format that differs from the summary
            // search (no playlistItemData, different subtitles), so isEpisode detection fails for
            // many items. The summary's episodes are parsed reliably, so they are used instead.
            loadSummaryPage(force)
            if (summaryLoadFailed) {
                filterLoadFailures[filterValue] = true
            } else {
                viewStateMap[filterValue] = ItemsPage(summaryItemsFor(filterValue), null)
            }
            return
        }

        // Album results rely on the summary to fill gaps, so make sure it is available.
        if (filter == YouTube.SearchFilter.FILTER_ALBUM) loadSummaryPage()

        YouTube
            .search(query, filter)
            .onSuccess { result ->
                val resolvedItems = resolveSearchMetadata(result.items)
                viewStateMap[filterValue] =
                    ItemsPage(
                        hiddenContentFilter((summaryItemsFor(filterValue) + resolvedItems).distinctBy { it.id }),
                        result.continuation,
                    )
            }.onFailure {
                filterLoadFailures[filterValue] = true
                reportException(it)
            }
    }

    init {
        viewModelScope.launch {
            // collectLatest: switching tabs cancels the load of the tab the user left instead of
            // queueing behind it. A cancelled tab has no page yet and loads again when reopened.
            filter.collectLatest { filter ->
                if (filter == null) loadSummaryPage() else loadFilteredPage(filter)
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val currentFilter = filter.value
            if (currentFilter == null) loadSummaryPage(force = true) else loadFilteredPage(currentFilter, force = true)
        }
    }

    fun loadMore() {
        val currentFilter = filter.value
        val filterValue = currentFilter?.value ?: return
        if (loadingMore) return
        val viewState = viewStateMap[filterValue] ?: return
        val continuation = viewState.continuation ?: return
        loadingMore = true
        viewModelScope.launch {
            try {
                val searchResult = YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                // The filter may have been reloaded while this page was in flight.
                val latest = viewStateMap[filterValue]
                if (latest == null || latest.continuation != continuation) return@launch
                val newItems = hiddenContentFilter(resolveSearchMetadata(searchResult.items))
                viewStateMap[filterValue] = ItemsPage(
                    (latest.items + newItems).distinctBy { it.id },
                    searchResult.continuation?.takeIf { it != continuation },
                )
            } finally {
                loadingMore = false
            }
        }
    }
}
