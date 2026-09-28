/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.OnboardingDoneKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) : ViewModel() {
        /** Artists offered: the charts' top artists, or what the search found. */
        private val _artists = MutableStateFlow<List<ArtistItem>>(emptyList())
        val artists = _artists.asStateFlow()

        private val _loading = MutableStateFlow(true)
        val loading = _loading.asStateFlow()

        private val _failed = MutableStateFlow(false)
        val failed = _failed.asStateFlow()

        /** Picked artists by id, kept in the order they were picked, whatever the list shows now. */
        private val _picked = MutableStateFlow<Map<String, ArtistItem>>(emptyMap())
        val picked = _picked.asStateFlow()

        private var popular: List<ArtistItem> = emptyList()
        private var searchJob: Job? = null
        private var finishing = false

        init {
            loadPopular()
        }

        fun loadPopular() {
            viewModelScope.launch {
                _loading.value = true
                _failed.value = false
                // Charts are not published in every country; the global chart playlist stands in for them.
                val items =
                    YouTube.getChartsPage().getOrNull()?.sections?.flatMap { it.items }?.takeIf { it.isNotEmpty() }
                        ?: YouTube.playlist(GLOBAL_CHART_PLAYLIST).getOrNull()?.songs
                if (items == null) {
                    _failed.value = true
                } else {
                    val listed = items.filterIsInstance<ArtistItem>().distinctBy { it.id }
                    // Some regions' charts list only songs; their artists are looked up by name to get a photo.
                    val fromSongs =
                        if (listed.size >= MAX_ARTISTS / 2) {
                            emptyList()
                        } else {
                            // A few lookups at a time: dozens at once only get the requests throttled.
                            val lookups = Semaphore(PARALLEL_LOOKUPS)
                            items
                                .filterIsInstance<SongItem>()
                                .flatMap { it.artists }
                                .filter { it.id != null && listed.none { a -> a.id == it.id } }
                                .distinctBy { it.id }
                                .take(MAX_ARTISTS - listed.size)
                                .map { artist ->
                                    async {
                                        lookups.withPermit {
                                            searchArtists(artist.name)?.let { found ->
                                                found.firstOrNull { it.id == artist.id } ?: found.firstOrNull()
                                            }
                                        }
                                    }
                                }.awaitAll()
                                .filterNotNull()
                        }
                    popular = (listed + fromSongs).distinctBy { it.id }.take(MAX_ARTISTS)
                    _artists.value = popular
                    _failed.value = popular.isEmpty()
                }
                _loading.value = false
            }
        }

        fun search(query: String) {
            searchJob?.cancel()
            if (query.isBlank()) {
                _artists.value = popular
                _loading.value = false
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(350)
                    _loading.value = true
                    YouTube
                        .search(query.trim(), YouTube.SearchFilter.FILTER_ARTIST)
                        .onSuccess { result ->
                            _artists.value = result.items.filterIsInstance<ArtistItem>().take(MAX_ARTISTS)
                            _failed.value = false
                        }.onFailure { _failed.value = true }
                    _loading.value = false
                }
        }

        private suspend fun searchArtists(name: String): List<ArtistItem>? =
            YouTube
                .search(name, YouTube.SearchFilter.FILTER_ARTIST)
                .getOrNull()
                ?.items
                ?.filterIsInstance<ArtistItem>()

        fun toggle(artist: ArtistItem) {
            _picked.update { if (artist.id in it) it - artist.id else it + (artist.id to artist) }
        }

        /** Adds the picks to the library's artists, which home, new releases and the DJ build on. */
        fun finish(onDone: () -> Unit) {
            // A second tap while the first is saving must not save and leave twice.
            if (finishing) return
            finishing = true
            val chosen = _picked.value.values.toList()
            viewModelScope.launch {
                if (chosen.isNotEmpty()) {
                    database.transaction {
                        chosen.forEach { artist ->
                            val entity =
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    channelId = artist.channelId,
                                    thumbnailUrl = artist.thumbnail,
                                    bookmarkedAt = LocalDateTime.now(),
                                )
                            insert(entity)
                            update(entity)
                        }
                    }
                }
                context.safeDataStoreEdit { it[OnboardingDoneKey] = true }
                onDone()
            }
        }

        private companion object {
            const val MAX_ARTISTS = 30
            const val PARALLEL_LOOKUPS = 4
        }
    }
