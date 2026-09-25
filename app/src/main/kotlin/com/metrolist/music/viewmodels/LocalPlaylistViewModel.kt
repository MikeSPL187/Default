/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.reversed
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.dataStore
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _onlinePlaylist = MutableStateFlow<PlaylistItem?>(null)
    val onlinePlaylist: StateFlow<PlaylistItem?> = _onlinePlaylist
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    ""
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }
            // Re-sorted on every song change (play counts included); keep it off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _suggestions = MutableStateFlow<List<SongItem>>(emptyList())

    /** Songs like the ones in this playlist that it does not hold yet. */
    val suggestions: StateFlow<List<SongItem>> = _suggestions
    private var suggestionsRequested = false

    /**
     * Looks up songs like a few of the playlist's own: each one's related page, and its radio in
     * case that page is empty. Asked for once when shown online; tried again later if it found nothing.
     */
    fun loadSuggestions() {
        if (suggestionsRequested) return
        suggestionsRequested = true
        viewModelScope.launch(Dispatchers.IO) {
            val songs = playlistSongs.first { it.isNotEmpty() }
            val present = songs.mapTo(HashSet()) { it.song.id }
            val perSeed =
                coroutineScope {
                    songs.shuffled().take(SUGGESTION_SEEDS).map { seed -> async { similarTo(seed.song.id) } }.awaitAll()
                }
            // Taken in turns from each seed, so the picks are not all like one song.
            val mixed =
                (0 until (perSeed.maxOfOrNull { it.size } ?: 0))
                    .flatMap { index -> perSeed.mapNotNull { it.getOrNull(index) } }
                    .distinctBy { it.id }
                    .filter { it.id !in present }
            _suggestions.value = mixed.take(SUGGESTION_COUNT)
            if (mixed.isEmpty()) suggestionsRequested = false
        }
    }

    private suspend fun similarTo(songId: String): List<SongItem> {
        val radio =
            YouTube.next(WatchEndpoint(videoId = songId, playlistId = "RDAMVM$songId"))
                .onFailure { Timber.tag("PlaylistSuggestions").w(it, "No radio for $songId") }
                .getOrNull()
        val related = radio?.relatedEndpoint?.let { YouTube.related(it).getOrNull()?.songs }.orEmpty()
        return related + radio?.items.orEmpty().filter { it.id != songId }
    }

    fun addSuggestion(song: SongItem) {
        _suggestions.value -= song
        viewModelScope.launch(Dispatchers.IO) {
            val target = playlist.value ?: return@launch
            database.insert(song.toMediaMetadata())
            database.addSongsToPlaylist(target, listOf(song.id to null))
            target.playlist.browseId?.let { syncUtils.scheduleAddToPlaylist(it, target.id, listOf(song.id)) }
        }
    }

    init {
        // Make positions consecutive so drag-and-drop moves work. This reads every row of the
        // playlist: the displayed list starts empty and may be filtered (search, hidden videos).
        database.transaction {
            playlistSongMaps(playlistId, from = 0)
                .sortedWith(compareBy({ it.position }, { it.id }))
                .forEachIndexed { index, map ->
                    if (map.position != index) update(map.copy(position = index))
                }
        }

        viewModelScope.launch {
            val localPlaylist = playlist.first { it != null }
            val browseId = localPlaylist?.playlist?.browseId
            if (browseId != null) {
                val page = withContext(Dispatchers.IO) {
                    YouTube.playlist(browseId).getOrNull()
                }
                val online = page?.playlist
                _onlinePlaylist.value = online
            }
        }
    }
}

private const val SUGGESTION_SEEDS = 3
private const val SUGGESTION_COUNT = 6
