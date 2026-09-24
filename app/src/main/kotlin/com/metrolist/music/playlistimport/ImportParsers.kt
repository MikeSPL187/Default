package com.metrolist.music.playlistimport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// Pure parsing of links and of what the services answer, kept apart from the network so it can be
// tested against recorded responses. The JSON is walked loosely: services add and rename fields,
// and a missing optional field must never lose the whole playlist.

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private val YANDEX_HOST = Regex("""^(?:https?://)?(?:www\.)?music\.yandex\.[a-z]{2,3}/""", RegexOption.IGNORE_CASE)
private val YANDEX_USER_PLAYLIST = Regex("""users/([^/?#]+)/playlists/(\d+)""")
private val YANDEX_PLAYLIST = Regex("""playlists/([A-Za-z0-9.\-]+)""")
private val YANDEX_ALBUM = Regex("""album/(\d+)""")
private val SPOTIFY_LINK = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(?:embed/)?(playlist|album)/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
private val SPOTIFY_URI = Regex("""^spotify:(playlist|album):([A-Za-z0-9]+)$""")

/** Recognises a Yandex Music or Spotify playlist or album link; null for anything else. */
fun parseImportLink(input: String): ImportLink? {
    val text = input.trim()
    SPOTIFY_URI.find(text)?.let { return spotifyLink(it.groupValues[1], it.groupValues[2]) }
    SPOTIFY_LINK.find(text)?.let { return spotifyLink(it.groupValues[1], it.groupValues[2]) }
    val yandex = YANDEX_HOST.find(text) ?: return null
    val path = text.substring(yandex.range.last + 1)
    YANDEX_USER_PLAYLIST.find(path)?.let { return ImportLink.YandexUserPlaylist(it.groupValues[1], it.groupValues[2]) }
    // An album link may continue to a single track (album/1/track/2); the album is what is shared.
    YANDEX_ALBUM.find(path)?.let { return ImportLink.YandexAlbum(it.groupValues[1]) }
    YANDEX_PLAYLIST.find(path)?.let { return ImportLink.YandexPlaylist(it.groupValues[1]) }
    return null
}

private fun spotifyLink(type: String, id: String): ImportLink =
    if (type.equals("album", ignoreCase = true)) ImportLink.SpotifyAlbum(id) else ImportLink.SpotifyPlaylist(id)

private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
private val JsonElement?.arr: JsonArray? get() = this as? JsonArray
private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** One Yandex track object: the title with its version ("Remix") and every artist. */
internal fun parseYandexTrack(track: JsonObject): ImportedTrack? {
    val title = track["title"].str ?: return null
    val version = track["version"].str
    val artists = track["artists"].arr.orEmpty().mapNotNull { it.obj?.get("name").str }
    return ImportedTrack(
        title = if (version != null) "$title ($version)" else title,
        artists = artists,
        durationSec = (track["durationMs"] as? JsonPrimitive)?.longOrNull?.let { (it / 1000).toInt() },
        album = track["albums"].arr?.firstOrNull()?.obj?.get("title").str,
        coverUrl = yandexCover(track["coverUri"].str ?: track["albums"].arr?.firstOrNull()?.obj?.get("coverUri").str, size = "200x200"),
    )
}

/** A Yandex cover template ("avatars.yandex.net/.../%%") made into a real image address. */
internal fun yandexCover(
    template: String?,
    size: String = "400x400",
): String? =
    template?.replace("%%", size)?.let { if (it.startsWith("http")) it else "https://$it" }

/**
 * A Yandex playlist (`/users/…/playlists/…` or `/playlist/<uuid>`). Entries may carry the full
 * track or only its id; the ids are returned so the caller can fetch those tracks separately.
 */
internal fun parseYandexPlaylist(body: String): Pair<ImportedPlaylist, List<String>> {
    val result = json.parseToJsonElement(body).jsonObject["result"].obj ?: throw ImportException(ImportError.NOT_FOUND)
    val tracks = mutableListOf<ImportedTrack>()
    val missingIds = mutableListOf<String>()
    result["tracks"].arr.orEmpty().forEach { entry ->
        val item = entry.obj ?: return@forEach
        val track = item["track"].obj
        if (track != null) {
            parseYandexTrack(track)?.let(tracks::add)
        } else {
            (item["id"] as? JsonPrimitive)?.contentOrNull?.let(missingIds::add)
        }
    }
    val playlist =
        ImportedPlaylist(
            title = result["title"].str ?: "Yandex Music",
            source = ImportSource.YANDEX_MUSIC,
            tracks = tracks,
            coverUrl = yandexCover(result["cover"].obj?.get("uri").str ?: result["ogImage"].str),
        )
    return playlist to missingIds
}

/** The answer to `/tracks`: the full tracks for ids a playlist listed bare. */
internal fun parseYandexTracks(body: String): List<ImportedTrack> =
    json.parseToJsonElement(body).jsonObject["result"].arr.orEmpty().mapNotNull { it.obj?.let(::parseYandexTrack) }

/** A Yandex album with its tracks, all discs in order. */
internal fun parseYandexAlbum(body: String): ImportedPlaylist {
    val result = json.parseToJsonElement(body).jsonObject["result"].obj ?: throw ImportException(ImportError.NOT_FOUND)
    val tracks = result["volumes"].arr.orEmpty().flatMap { disc -> disc.arr.orEmpty().mapNotNull { it.obj?.let(::parseYandexTrack) } }
    val artist = result["artists"].arr?.firstOrNull()?.obj?.get("name").str
    val title = result["title"].str ?: "Yandex Music"
    return ImportedPlaylist(
        title = if (artist != null) "$artist — $title" else title,
        source = ImportSource.YANDEX_MUSIC,
        tracks = tracks,
        coverUrl = yandexCover(result["coverUri"].str ?: result["ogImage"].str),
    )
}

private val NEXT_DATA = Regex("""<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
private val ARTIST_SEPARATOR = Regex(""",[\s ]+""")

/** Spotify's embed page shows at most this many tracks to visitors who are not signed in. */
internal const val SPOTIFY_EMBED_LIMIT = 100

/** The track list in a Spotify embed page, which Spotify serves without an account. */
internal fun parseSpotifyEmbed(html: String): ImportedPlaylist {
    val data = NEXT_DATA.find(html)?.groupValues?.get(1) ?: throw ImportException(ImportError.UNREADABLE)
    val entity =
        json.parseToJsonElement(data).jsonObject["props"].obj?.get("pageProps").obj
            ?.get("state").obj?.get("data").obj?.get("entity").obj
            ?: throw ImportException(ImportError.NOT_FOUND)
    val tracks =
        entity["trackList"].arr.orEmpty().mapNotNull { element ->
            val item = element.obj ?: return@mapNotNull null
            val title = item["title"].str ?: return@mapNotNull null
            ImportedTrack(
                title = title,
                artists = item["subtitle"].str?.split(ARTIST_SEPARATOR)?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
                durationSec = (item["duration"] as? JsonPrimitive)?.longOrNull?.let { (it / 1000).toInt() },
            )
        }
    val cover =
        entity["coverArt"].obj?.get("sources").arr?.maxByOrNull { (it.obj?.get("width") as? JsonPrimitive)?.intOrNull ?: 0 }
            ?.obj?.get("url").str
            ?: entity["visualIdentity"].obj?.get("image").arr?.lastOrNull()?.obj?.get("url").str
    return ImportedPlaylist(
        title = entity["name"].str ?: entity["title"].str ?: "Spotify",
        source = ImportSource.SPOTIFY,
        tracks = tracks,
        coverUrl = cover,
        truncated = tracks.size >= SPOTIFY_EMBED_LIMIT,
    )
}

// Only a number followed by a mark is a list number: "7 Rings" is a title.
private val LEADING_NUMBER = Regex("""^\s*\d{1,4}[.):]\s+""")
private val DASH_SEPARATOR = Regex("""\s+[-–—]\s+""")

/**
 * A pasted list, one track per line: "Artist — Title" (any dash), numbered or not. A line with no
 * dash is taken as a title alone and matched on that.
 */
fun parseTrackList(text: String): List<ImportedTrack> =
    text.lines().mapNotNull { raw ->
        val line = raw.trim().replace(LEADING_NUMBER, "").trim()
        if (line.isEmpty()) return@mapNotNull null
        val parts = line.split(DASH_SEPARATOR, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            ImportedTrack(
                title = parts[1].trim(),
                artists = parts[0].split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?)\s*""", RegexOption.IGNORE_CASE)).map(String::trim).filter(String::isNotEmpty),
            )
        } else {
            ImportedTrack(title = line, artists = emptyList())
        }
    }
