package com.metrolist.music.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the home screen offers while only music on the device can play. */
@HiltViewModel
class OfflineHomeViewModel
    @Inject
    constructor(
        database: MusicDatabase,
    ) : ViewModel() {
        /** Null until the database answers, so an empty library is not shown for a moment. */
        val allSongs = database.downloadedSongsByCreateDateAsc().map { it.asReversed() }.shared()
        val recent = database.recentlyPlayedDownloadedSongs(RECENT_LIMIT).shared()
        val liked = database.likedDownloadedSongs().shared()
        val playlists = database.playlistsWithDownloads().shared()
        val albums = database.albumsDownloadedByCreateDateAsc().map { it.asReversed() }.shared()

        private fun <T> Flow<T>.shared() = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private companion object {
            const val RECENT_LIMIT = 8
        }
    }
