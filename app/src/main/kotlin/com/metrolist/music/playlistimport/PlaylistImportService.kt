package com.metrolist.music.playlistimport

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Reads playlists from Yandex Music and Spotify, without an account, and finds their songs on YouTube Music. */
object PlaylistImportService {
    private const val YANDEX_API = "https://api.music.yandex.net"
    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = false
        }
    }

    suspend fun fetch(link: ImportLink): ImportedPlaylist {
        val playlist =
            try {
                when (link) {
                    is ImportLink.YandexUserPlaylist -> yandexPlaylist("$YANDEX_API/users/${link.owner}/playlists/${link.kind}")
                    is ImportLink.YandexPlaylist -> yandexPlaylist("$YANDEX_API/playlist/${link.uuid}")
                    is ImportLink.YandexAlbum -> parseYandexAlbum(getText("$YANDEX_API/albums/${link.id}/with-tracks"))
                    is ImportLink.SpotifyPlaylist -> parseSpotifyEmbed(getText("https://open.spotify.com/embed/playlist/${link.id}"))
                    is ImportLink.SpotifyAlbum -> parseSpotifyEmbed(getText("https://open.spotify.com/embed/album/${link.id}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                throw e
            } catch (e: IOException) {
                throw ImportException(ImportError.NETWORK, e)
            } catch (e: Exception) {
                throw ImportException(ImportError.UNREADABLE, e)
            }
        if (playlist.tracks.isEmpty()) throw ImportException(ImportError.EMPTY)
        return playlist
    }

    private suspend fun getText(url: String): String {
        val response =
            client.get(url) {
                header("User-Agent", BROWSER_AGENT)
                header("Accept-Language", "ru,en;q=0.8")
            }
        return response.checked().bodyAsText()
    }

    private fun HttpResponse.checked(): HttpResponse =
        when {
            status.isSuccess() -> this
            // A private playlist answers as if it did not exist.
            status == HttpStatusCode.NotFound || status == HttpStatusCode.Forbidden || status == HttpStatusCode.Unauthorized ->
                throw ImportException(ImportError.NOT_FOUND)
            else -> throw ImportException(ImportError.NETWORK)
        }

    /** Large Yandex playlists list only track ids past a point; those are fetched in batches. */
    private suspend fun yandexPlaylist(url: String): ImportedPlaylist {
        val (playlist, missingIds) = parseYandexPlaylist(getText("$url?rich-tracks=true"))
        if (missingIds.isEmpty()) return playlist
        val rest =
            missingIds.chunked(200).flatMap { ids ->
                val response =
                    client.submitForm(url = "$YANDEX_API/tracks", formParameters = parameters { append("track-ids", ids.joinToString(",")) }) {
                        header("Accept-Language", "ru,en;q=0.8")
                    }
                parseYandexTracks(response.checked().bodyAsText())
            }
        return playlist.copy(tracks = playlist.tracks + rest)
    }

    private fun SongItem.toCandidate() = MatchCandidate(id, title, artists.map { it.name }, duration)

    private suspend fun search(query: String, filter: YouTube.SearchFilter): List<SongItem> =
        YouTube.search(query, filter).getOrNull()?.items.orEmpty().filterIsInstance<SongItem>().take(6)

    /** Songs for a query typed by hand, when the automatic match was wrong or missing. */
    suspend fun searchSongs(query: String): List<SongItem> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()

    /**
     * The YouTube Music song for [track], or null when nothing is close enough. Songs are searched
     * first; only if none fits are music videos tried, since some releases exist only as those.
     */
    suspend fun resolve(
        track: ImportedTrack,
        allowSwap: Boolean,
    ): SongItem? {
        val query = (track.artists.take(2) + track.title).joinToString(" ")
        for (filter in listOf(YouTube.SearchFilter.FILTER_SONG, YouTube.SearchFilter.FILTER_VIDEO)) {
            val results = search(query, filter)
            val best = TrackMatcher.pick(track, results.map { it.toCandidate() }, allowSwap) ?: continue
            return results.first { it.id == best.id }
        }
        return null
    }
}
