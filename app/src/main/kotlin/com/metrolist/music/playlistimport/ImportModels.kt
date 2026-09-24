package com.metrolist.music.playlistimport

/** A track as another service lists it, before it is looked up on YouTube Music. */
data class ImportedTrack(
    val title: String,
    val artists: List<String>,
    val durationSec: Int? = null,
    val album: String? = null,
)

enum class ImportSource { YANDEX_MUSIC, SPOTIFY, TEXT }

data class ImportedPlaylist(
    val title: String,
    val source: ImportSource,
    val tracks: List<ImportedTrack>,
    val coverUrl: String? = null,
    /** The service showed only part of the list, as Spotify does for anonymous visitors. */
    val truncated: Boolean = false,
)

/** What a pasted link points at. */
sealed interface ImportLink {
    data class YandexUserPlaylist(val owner: String, val kind: String) : ImportLink

    /** The newer share links, `music.yandex.ru/playlists/<uuid>`. */
    data class YandexPlaylist(val uuid: String) : ImportLink

    data class YandexAlbum(val id: String) : ImportLink

    data class SpotifyPlaylist(val id: String) : ImportLink

    data class SpotifyAlbum(val id: String) : ImportLink
}

/** Why a playlist could not be read, each shown to the user in its own words. */
enum class ImportError { UNSUPPORTED_LINK, NOT_FOUND, NETWORK, EMPTY, UNREADABLE }

class ImportException(val error: ImportError, cause: Throwable? = null) : Exception(error.name, cause)
