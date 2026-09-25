package com.metrolist.music.dj

import android.content.Context
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.utils.filterNotRecommended
import com.metrolist.music.utils.notRecommended
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

/**
 * The DJ: an endless set mixing the user's favourites with songs new to them, found through the
 * radio of what was just picked. Each page carries on from the last, and nothing repeats.
 */
class DjQueue(
    private val title: String,
    private val database: MusicDatabase,
    private val context: Context,
    private val familiarShare: Double = DjPlanner.BALANCED,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val played = HashSet<String>()
    private var seeds: List<String> = emptyList()

    override suspend fun getInitialStatus() = Queue.Status(title, nextBatch(), 0)

    override fun hasNextPage() = true

    override suspend fun nextPage(): List<MediaItem> = nextBatch()

    private sealed interface Pick {
        val id: String
        val artist: String?

        data class Known(val song: Song) : Pick {
            override val id get() = song.id
            override val artist get() = song.artists.firstOrNull()?.id
        }

        data class Fresh(val song: SongItem) : Pick {
            override val id get() = song.id
            override val artist get() = song.artists.firstOrNull()?.id
        }
    }

    private suspend fun nextBatch(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val blocked = context.notRecommended()
            val favorites =
                (
                    database.mostPlayedSongs(LocalDateTime.now().minusDays(FAVORITES_DAYS), limit = FAVORITES_POOL).first() +
                        database.likedSongsByCreateDateAsc().first().takeLast(FAVORITES_POOL)
                ).distinctBy { it.id }
                    .filterNotRecommended(blocked)
                    .filter { !it.song.isEpisode && it.id !in played }
                    .shuffled()
            val favoriteIds = favorites.mapTo(HashSet()) { it.id }
            val from = seeds.ifEmpty { favorites.take(SEEDS).map { it.id } }.shuffled().take(SEEDS)
            val discoveries =
                coroutineScope { from.map { async { radioOf(it) } }.awaitAll() }
                    .flatten()
                    .distinctBy { it.id }
                    .filterNotRecommended(blocked)
                    .filter { it.id !in played && it.id !in favoriteIds }
                    .shuffled()
            val set =
                DjPlanner.mix(
                    familiar = favorites.map { Pick.Known(it) },
                    discoveries = discoveries.map { Pick.Fresh(it) },
                    familiarShare = familiarShare,
                    size = BATCH,
                    artistOf = { it.artist },
                )
            set.mapTo(played) { it.id }
            // The next page grows out of what this one played, new songs first.
            seeds = (set.filterIsInstance<Pick.Fresh>() + set.filterIsInstance<Pick.Known>()).take(SEEDS).map { it.id }
            set.map { pick ->
                when (pick) {
                    is Pick.Known -> pick.song.toMediaItem()
                    is Pick.Fresh -> pick.song.toMediaItem()
                }
            }
        }

    private suspend fun radioOf(songId: String): List<SongItem> =
        YouTube.next(WatchEndpoint(videoId = songId, playlistId = "RDAMVM$songId"))
            .onFailure { Timber.tag("DJ").w(it, "No radio for $songId") }
            .getOrNull()
            ?.items
            .orEmpty()
            .filter { it.id != songId }

    private companion object {
        const val BATCH = 20
        const val SEEDS = 3
        const val FAVORITES_POOL = 80
        const val FAVORITES_DAYS = 120L
    }
}
