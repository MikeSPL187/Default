/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import android.graphics.Bitmap
import com.metrolist.music.constants.ArtistSongSortType
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.entities.PlaylistEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

sealed class PlaylistCreationState {
    object Idle : PlaylistCreationState()
    object Creating : PlaylistCreationState()
    object Success : PlaylistCreationState()
}

class WrappedManager(
    private val databaseDao: DatabaseDao,
    private val context: Context,
    val period: WrappedPeriod,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Fixed when the recap opens so every page and the saved playlist cover the same stretch. */
    val now: LocalDateTime = LocalDateTime.now()
    private val range = period.range(now)

    private val _state = MutableStateFlow(WrappedState(bigLabel = period.bigLabel(now, firstListen = null)))
    val state = _state.asStateFlow()

    fun createPlaylist(cover: Bitmap, playlistName: String) {
        if (_state.value.playlistCreationState != PlaylistCreationState.Idle) return

        _state.update { it.copy(playlistCreationState = PlaylistCreationState.Creating) }
        scope.launch {
            try {
                // Leaving the recap must not stop the save halfway and leave an empty playlist behind.
                withContext(Dispatchers.IO + NonCancellable) {
                    val allSongs = databaseDao.mostPlayedSongsStats(range.from, toTimeStamp = range.to, limit = -1).first()

                    val playlistId = UUID.randomUUID().toString()

                    // Kept in app storage, not the cache, so the system never takes the cover away.
                    val file = File(File(context.filesDir, "playlist_covers").apply { mkdirs() }, "$playlistId.png")
                    file.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }

                    val newPlaylist = PlaylistEntity(
                        id = playlistId,
                        name = playlistName,
                        thumbnailUrl = file.toURI().toString(),
                        bookmarkedAt = LocalDateTime.now(),
                        isEditable = true
                    )
                    databaseDao.insert(newPlaylist)

                    val createdPlaylist = databaseDao.playlist(playlistId).first()
                    if (createdPlaylist != null) {
                        val songIds = allSongs.map { it.id to null }
                        databaseDao.addSongsToPlaylist(createdPlaylist, songIds)
                    } else {
                        Timber.tag("WrappedManager")
                            .e("Failed to retrieve created playlist with id: $playlistId")
                    }
                }
                _state.update { it.copy(playlistCreationState = PlaylistCreationState.Success) }
            } catch (e: Exception) {
                Timber.tag("WrappedManager").e(e, "Error saving wrapped playlist")
                _state.update { it.copy(playlistCreationState = PlaylistCreationState.Idle) }
            }
        }
    }

    private suspend fun generatePlaylistMap() {
        val topSongs = _state.value.topSongs
        val topArtists = _state.value.topArtists
        if (topSongs.isEmpty()) {
            Timber.tag("WrappedManager").w("Cannot generate playlist map, top songs list is empty.")
            _state.update { it.copy(trackMap = emptyMap()) }
            return
        }

        withContext(Dispatchers.IO) {
            val playlistMap = mutableMapOf<WrappedScreenType, String>()

            // Intro Part: Random song from top 6-30
            val introSongPool = topSongs.drop(5)
            val introSong = introSongPool.randomOrNull()?.id ?: topSongs.last().id
            playlistMap[WrappedScreenType.Welcome] = introSong
            playlistMap[WrappedScreenType.MinutesTease] = introSong
            playlistMap[WrappedScreenType.MinutesReveal] = introSong

            // Music Part: Top 1 song
            val topSong = topSongs.first()
            playlistMap[WrappedScreenType.TotalSongs] = topSong.id
            playlistMap[WrappedScreenType.TopSongReveal] = topSong.id
            playlistMap[WrappedScreenType.Top5Songs] = topSong.id

            // Album Part: the top album's most played song
            val albumSong = _state.value.topAlbums.firstOrNull()?.let { album ->
                databaseDao.mostPlayedSongOfAlbum(album.id, range.from, range.to)
            } ?: topSong.id
            playlistMap[WrappedScreenType.TotalAlbums] = albumSong
            playlistMap[WrappedScreenType.TopAlbumReveal] = albumSong
            playlistMap[WrappedScreenType.Top5Albums] = albumSong

            // Artist Part: Top artist's song with specific rule
            val topArtist = topArtists.firstOrNull()

            val artistSong = topArtist?.let { artist ->
                val artistTopSongs = databaseDao.artistSongs(
                    artistId = artist.id,
                    sortType = ArtistSongSortType.PLAY_TIME,
                    descending = true,
                    fromTimeStamp = range.from,
                    toTimeStamp = range.to
                ).first()
                if (artistTopSongs.isNotEmpty()) {
                    val artistTopSong = artistTopSongs.first()
                    if (artistTopSong.id == topSong.id) {
                        // Overlap: Use the artist's second song.
                        // If a second song doesn't exist, use a random song from their list.
                        artistTopSongs.getOrNull(1)?.id ?: artistTopSongs.filter { it.id != topSong.id }.randomOrNull()?.id ?: artistTopSong.id
                    } else {
                        artistTopSong.id
                    }
                } else {
                    // Data anomaly: Fallback to the user's top song.
                    topSong.id
                }
            } ?: topSong.id // Fallback if no top artist.
            playlistMap[WrappedScreenType.TotalArtists] = artistSong
            playlistMap[WrappedScreenType.TopArtistReveal] = artistSong
            playlistMap[WrappedScreenType.Top5Artists] = artistSong

            // End Part
            val endSongPool = topSongs.drop(2).take(3)
            val endSong = endSongPool.randomOrNull()?.id ?: topSong.id
            playlistMap[WrappedScreenType.Playlist] = endSong
            playlistMap[WrappedScreenType.Conclusion] = "2-p9DM2Xvsc"

            Timber.tag("WrappedManager").d("Generated Playlist Map: $playlistMap")
            _state.update { it.copy(trackMap = playlistMap) }
        }
    }

    suspend fun prepare() {
        if (_state.value.isDataReady) return
        Timber.tag("WrappedManager").d("Starting Wrapped data preparation for $period")

        val (from, to) = range
        withContext(Dispatchers.IO) {
            val topSongs = async { databaseDao.mostPlayedSongsStats(from, toTimeStamp = to, limit = 30).first() }
            val topArtists = async { databaseDao.mostPlayedArtists(from, toTimeStamp = to, limit = 5).first() }
            val topAlbums = async { databaseDao.mostPlayedAlbumStats(from, to, limit = 5) }
            val uniqueSongCount = async { databaseDao.getUniqueSongCountInRange(from, to).first() }
            val uniqueArtistCount = async { databaseDao.getUniqueArtistCountInRange(from, to).first() }
            val uniqueAlbumCount = async { databaseDao.getUniqueAlbumCountInRange(from, to).first() }
            val totalPlayTimeMs = async { databaseDao.getTotalPlayTimeInRange(from, to).first() ?: 0L }
            val firstListen = async { databaseDao.firstListenTime() }

            _state.update {
                it.copy(
                    topSongs = topSongs.await(),
                    topArtists = topArtists.await(),
                    topAlbums = topAlbums.await(),
                    uniqueSongCount = uniqueSongCount.await(),
                    uniqueArtistCount = uniqueArtistCount.await(),
                    totalAlbums = uniqueAlbumCount.await(),
                    totalMinutes = totalPlayTimeMs.await() / 1000 / 60,
                    elapsedDays = period.elapsedDays(now, firstListen.await()),
                    bigLabel = period.bigLabel(now, firstListen.await()),
                )
            }
        }

        generatePlaylistMap()
        _state.update { it.copy(isDataReady = true) }
        Timber.tag("WrappedManager").d("Wrapped data preparation finished")
    }

    fun dispose() {
        scope.cancel()
    }
}
