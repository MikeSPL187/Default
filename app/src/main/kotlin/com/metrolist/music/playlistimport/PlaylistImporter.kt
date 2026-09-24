package com.metrolist.music.playlistimport

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class TrackStatus { PENDING, FOUND, NOT_FOUND }

data class ImportState(
    val phase: Phase = Phase.INPUT,
    val playlist: ImportedPlaylist? = null,
    val statuses: List<TrackStatus> = emptyList(),
    val error: ImportError? = null,
    /** The playlist created in the library once the import finished. */
    val playlistId: String? = null,
) {
    enum class Phase { INPUT, LOADING, PREVIEW, IMPORTING, DONE }

    val found: Int get() = statuses.count { it == TrackStatus.FOUND }
    val processed: Int get() = statuses.count { it != TrackStatus.PENDING }
}

/**
 * Runs a playlist import from start to end. It lives with the app, not the screen, so leaving the
 * screen does not stop a long import, and coming back shows where it is.
 */
@Singleton
class PlaylistImporter
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null

        private val _state = MutableStateFlow(ImportState())
        val state = _state.asStateFlow()

        /** Reads the playlist behind a Yandex Music or Spotify link and shows it for review. */
        fun load(linkText: String) {
            val link = parseImportLink(linkText)
            if (link == null) {
                _state.value = ImportState(error = ImportError.UNSUPPORTED_LINK)
                return
            }
            job?.cancel()
            _state.value = ImportState(phase = ImportState.Phase.LOADING)
            job =
                scope.launch {
                    _state.value =
                        try {
                            preview(PlaylistImportService.fetch(link))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ImportException) {
                            ImportState(error = e.error)
                        } catch (e: Exception) {
                            Timber.tag("PlaylistImport").w(e, "Could not read $link")
                            ImportState(error = ImportError.UNREADABLE)
                        }
                }
        }

        /** Takes a pasted list of tracks, one per line. */
        fun loadList(
            title: String,
            text: String,
        ) {
            val tracks = parseTrackList(text)
            _state.value =
                if (tracks.isEmpty()) {
                    ImportState(error = ImportError.EMPTY)
                } else {
                    preview(ImportedPlaylist(title = title.trim(), source = ImportSource.TEXT, tracks = tracks))
                }
        }

        private fun preview(playlist: ImportedPlaylist) =
            ImportState(
                phase = ImportState.Phase.PREVIEW,
                playlist = playlist,
                statuses = List(playlist.tracks.size) { TrackStatus.PENDING },
            )

        /** Finds every track on YouTube Music and saves those found as a new library playlist. */
        fun start(title: String) {
            val playlist = _state.value.playlist ?: return
            if (_state.value.phase != ImportState.Phase.PREVIEW) return
            val named = playlist.copy(title = title.trim().ifEmpty { playlist.title })
            _state.update { it.copy(phase = ImportState.Phase.IMPORTING, playlist = named) }
            job =
                scope.launch {
                    val songs = resolveAll(named)
                    // A cancel landing while saving must not leave a half-filled playlist behind.
                    val playlistId = withContext(NonCancellable) { save(named, songs) }
                    _state.update { it.copy(phase = ImportState.Phase.DONE, playlistId = playlistId) }
                }
        }

        /** Stops a running import and returns to the review of the playlist. */
        fun cancel() {
            job?.cancel()
            _state.update { current ->
                current.playlist?.let { preview(it) } ?: ImportState()
            }
        }

        /** Back to an empty form, for the next import. */
        fun reset() {
            job?.cancel()
            _state.value = ImportState()
        }

        private suspend fun resolveAll(playlist: ImportedPlaylist): List<SongItem?> =
            coroutineScope {
                val semaphore = Semaphore(PARALLEL_SEARCHES)
                val allowSwap = playlist.source == ImportSource.TEXT
                playlist.tracks.mapIndexed { index, track ->
                    async {
                        semaphore.withPermit {
                            val song =
                                try {
                                    PlaylistImportService.resolve(track, allowSwap)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Timber.tag("PlaylistImport").w(e, "Could not look up ${track.title}")
                                    null
                                }
                            _state.update { current ->
                                // A result landing after a cancel belongs to no import any more.
                                if (current.phase != ImportState.Phase.IMPORTING) return@update current
                                current.copy(
                                    statuses =
                                        current.statuses.toMutableList().also {
                                            if (index < it.size) it[index] = if (song != null) TrackStatus.FOUND else TrackStatus.NOT_FOUND
                                        },
                                )
                            }
                            song
                        }
                    }
                }.awaitAll()
            }

        private suspend fun save(
            playlist: ImportedPlaylist,
            songs: List<SongItem?>,
        ): String {
            val found = songs.filterNotNull().distinctBy { it.id }
            found.forEach { song ->
                runCatching { database.insert(song.toMediaMetadata()) }
                    .onFailure { Timber.tag("PlaylistImport").w(it, "Could not store ${song.id}") }
            }
            val entity = PlaylistEntity(name = playlist.title, thumbnailUrl = playlist.coverUrl)
            database.insert(entity)
            database.playlist(entity.id).first()?.let { created ->
                database.addSongsToPlaylist(created, found.map { it.id to null })
            }
            return entity.id
        }

        private companion object {
            /** Searches at once; more only gets the requests throttled. */
            const val PARALLEL_SEARCHES = 4
        }
    }
