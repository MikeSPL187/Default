package com.metrolist.music.offline

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.PlaylistDownloadCount
import com.metrolist.music.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject

enum class OfflineSongSort { RECENTLY_PLAYED, RECENTLY_DOWNLOADED, NAME }

/** Everything on the device, with what the flow and the sorting need to know about each song. */
@Immutable
data class OfflineLibrary(
    /** Newest download first. */
    val songs: List<Song>,
    val tracks: List<FlowTrack>,
    val modeCounts: Map<FlowMode, Int>,
) {
    private val trackById = tracks.associateBy { it.id }

    fun sorted(sort: OfflineSongSort): List<Song> =
        when (sort) {
            OfflineSongSort.RECENTLY_DOWNLOADED -> songs
            OfflineSongSort.NAME -> songs.sortedBy { it.song.title.lowercase() }
            OfflineSongSort.RECENTLY_PLAYED ->
                songs.sortedWith(compareByDescending(nullsFirst()) { trackById[it.id]?.lastPlayed })
        }

    fun flow(
        mode: FlowMode,
        character: FlowCharacter,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<Song> {
        val songById = songs.associateBy { it.id }
        return FlowPlanner.order(FlowPlanner.candidates(tracks, mode, now), character, now).mapNotNull { songById[it.id] }
    }
}

/** An artist with songs on the device. */
@Immutable
data class OfflineArtist(
    val artist: ArtistEntity,
    val songs: List<Song>,
)

/** What the home screen offers while only music on the device can play. */
@HiltViewModel
class OfflineHomeViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        /** Null until the database answers, so an empty library is not shown for a moment. */
        val library =
            combine(database.downloadedSongsByCreateDateAsc(), database.downloadedSongStats()) { songs, stats ->
                val statsById = stats.associateBy { it.id }
                val newestFirst = songs.asReversed()
                val tracks =
                    newestFirst.map { song ->
                        val stat = statsById[song.id]
                        FlowTrack(
                            id = song.id,
                            artistKey = song.artists.firstOrNull()?.id,
                            liked = song.song.liked,
                            downloadedAt = song.song.dateDownload,
                            playTime = stat?.playTime ?: 0,
                            lastPlayed = stat?.lastPlayed,
                        )
                    }
                val now = LocalDateTime.now()
                OfflineLibrary(
                    songs = newestFirst,
                    tracks = tracks,
                    modeCounts = FlowMode.entries.associateWith { FlowPlanner.candidates(tracks, it, now).size },
                )
            }.flowOn(Dispatchers.Default).shared()

        val artists =
            database.downloadedSongsByCreateDateAsc().map { songs ->
                songs.asReversed()
                    .groupBy { it.artists.firstOrNull() }
                    .mapNotNull { (artist, artistSongs) -> artist?.let { OfflineArtist(it, artistSongs) } }
                    .sortedByDescending { it.songs.size }
            }.flowOn(Dispatchers.Default).shared()

        val playlists = database.playlistsWithDownloads().shared()

        val downloadCounts =
            database.playlistDownloadCounts().map { counts -> counts.associateBy(PlaylistDownloadCount::playlistId) }.shared()

        val albums = database.albumsDownloadedByCreateDateAsc().map { it.asReversed() }.shared()

        val likedTotal = database.likedSongsCount().shared()

        suspend fun playlistSongs(playlistId: String): List<Song> = database.downloadedPlaylistSongs(playlistId)

        suspend fun albumSongs(albumId: String): List<Song> = database.albumSongs(albumId).first().filter { it.song.isDownloaded }

        private fun <T> Flow<T>.shared() = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
