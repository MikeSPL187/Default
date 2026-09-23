package com.metrolist.music.utils

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.constants.NotRecommendedArtistIdsKey
import com.metrolist.music.constants.NotRecommendedSongIdsKey
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.metadata
import com.metrolist.music.playback.queues.Queue

/**
 * Songs and artists the user asked not to be recommended. They are left out of radio, automix and
 * home recommendations, but never removed from the user's own playlists, albums or library.
 */
data class NotRecommended(
    val songIds: Set<String> = emptySet(),
    val artistIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = songIds.isEmpty() && artistIds.isEmpty()

    fun blocks(songId: String, artistIds: Iterable<String?>): Boolean =
        songId in songIds || artistIds.any { it != null && it in this.artistIds }
}

fun Preferences.notRecommended() =
    NotRecommended(
        songIds = this[NotRecommendedSongIdsKey].orEmpty(),
        artistIds = this[NotRecommendedArtistIdsKey].orEmpty(),
    )

suspend fun Context.notRecommended(): NotRecommended = dataStore.read(NotRecommendedSongIdsKey, emptySet()).let { songs ->
    NotRecommended(songs, dataStore.read(NotRecommendedArtistIdsKey, emptySet()))
}

suspend fun Context.setSongNotRecommended(songId: String, notRecommended: Boolean) =
    safeDataStoreEdit { prefs ->
        val ids = prefs[NotRecommendedSongIdsKey].orEmpty()
        prefs[NotRecommendedSongIdsKey] = if (notRecommended) ids + songId else ids - songId
    }

suspend fun Context.setArtistNotRecommended(artistId: String, notRecommended: Boolean) =
    safeDataStoreEdit { prefs ->
        val ids = prefs[NotRecommendedArtistIdsKey].orEmpty()
        prefs[NotRecommendedArtistIdsKey] = if (notRecommended) ids + artistId else ids - artistId
    }

suspend fun Context.clearNotRecommended() =
    safeDataStoreEdit { prefs ->
        prefs.remove(NotRecommendedSongIdsKey)
        prefs.remove(NotRecommendedArtistIdsKey)
    }

fun List<MediaItem>.filterNotRecommended(notRecommended: NotRecommended): List<MediaItem> =
    if (notRecommended.isEmpty) {
        this
    } else {
        filterNot { item ->
            item.metadata?.let { notRecommended.blocks(it.id, it.artists.map { artist -> artist.id }) } == true
        }
    }

/** Filters a queue's items while keeping the song the user picked, and its index, intact. */
fun Queue.Status.filterNotRecommended(notRecommended: NotRecommended): Queue.Status {
    if (notRecommended.isEmpty || items.isEmpty()) return this
    val picked = items.getOrNull(mediaItemIndex)
    val before = items.take(mediaItemIndex).filterNotRecommended(notRecommended)
    val after = items.drop(mediaItemIndex + 1).filterNotRecommended(notRecommended)
    return if (picked == null) {
        copy(items = before + after, mediaItemIndex = 0)
    } else {
        copy(items = before + picked + after, mediaItemIndex = before.size)
    }
}

@JvmName("filterNotRecommendedSongs")
fun List<Song>.filterNotRecommended(notRecommended: NotRecommended): List<Song> =
    if (notRecommended.isEmpty) this else filterNot { notRecommended.blocks(it.id, it.artists.map { artist -> artist.id }) }

@JvmName("filterNotRecommendedItems")
fun <T : YTItem> List<T>.filterNotRecommended(notRecommended: NotRecommended): List<T> =
    if (notRecommended.isEmpty) {
        this
    } else {
        filterNot { item ->
            when (item) {
                is SongItem -> notRecommended.blocks(item.id, item.artists.map { it.id })
                is ArtistItem -> item.id in notRecommended.artistIds
                else -> false
            }
        }
    }
