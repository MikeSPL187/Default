package com.metrolist.music.dj

import android.content.Context
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.filterExplicit
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.utils.DayPart
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.filterNotRecommended
import com.metrolist.music.utils.notRecommended
import com.metrolist.music.utils.read
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

/**
 * The DJ: an endless set of the user's favourites and songs new to them. It learns while it
 * plays: a new song heard through leads the next set and asks for more new ones, a skipped one
 * asks for fewer, and an artist skipped tonight comes back far less.
 */
class DjQueue(
    private val title: String,
    private val database: MusicDatabase,
    private val context: Context,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val lock = Any()
    private val played = HashSet<String>()
    private val picks = HashMap<String, Pick>()
    private val artistSkips = HashMap<String, Int>()
    private val kept = ArrayDeque<String>()
    private var discoveriesKept = 0
    private var discoveriesSkipped = 0
    private var favouritesSkipped = 0

    override suspend fun getInitialStatus() = Queue.Status(title, nextBatch(FIRST_BATCH), 0)

    override fun hasNextPage() = true

    override suspend fun nextPage(): List<MediaItem> = nextBatch(BATCH)

    /** What the player saw of a song of this set when it moved on. */
    fun onPlayed(
        songId: String,
        playedMs: Long,
        durationMs: Long,
    ) = synchronized(lock) {
        val pick = picks[songId] ?: return
        when {
            DjPlanner.isSkip(playedMs, durationMs) -> {
                pick.artist?.let { artistSkips[it] = (artistSkips[it] ?: 0) + 1 }
                if (pick is Pick.Fresh) discoveriesSkipped++ else favouritesSkipped++
            }
            DjPlanner.isKept(playedMs, durationMs) -> {
                if (pick is Pick.Fresh) discoveriesKept++
                kept.addFirst(songId)
                while (kept.size > SEEDS * 2) kept.removeLast()
            }
        }
    }

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

    private suspend fun nextBatch(size: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val blocked = context.notRecommended()
            val hideExplicit = context.dataStore.read(HideExplicitKey, false)
            val hideVideos = context.dataStore.read(HideVideoSongsKey, false)
            val (skips, share, recentKeeps) =
                synchronized(lock) {
                    Triple(HashMap(artistSkips), DjPlanner.adaptShare(discoveriesKept, discoveriesSkipped, favouritesSkipped), kept.toList())
                }
            // Songs heard in the last hours, in the DJ or not, wait for another day.
            val recent = database.songIdsPlayedSince(LocalDateTime.now().minusHours(RECENT_HOURS)).toHashSet()
            val excluded = synchronized(lock) { played + recent }

            val mostPlayed = database.mostPlayedSongs(LocalDateTime.now().minusDays(FAVOURITES_DAYS), limit = FAVOURITES_POOL).first()
            val liked = database.likedSongsByCreateDateAsc().first().takeLast(FAVOURITES_POOL)
            val ofThisHour = database.songsPlayedAtHours(DayPart.now().hours, LocalDateTime.now().minusDays(HOUR_DAYS), HOUR_POOL)
            val likedIds = liked.mapTo(HashSet()) { it.id }
            val hourIds = ofThisHour.mapTo(HashSet()) { it.id }
            val rank = mostPlayed.withIndex().associate { it.value.id to it.index }
            val candidates =
                (mostPlayed + ofThisHour + liked)
                    .distinctBy { it.id }
                    .filterNot { it.song.isEpisode || it.id in excluded }
                    .filterNotRecommended(blocked)
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideos)
            val favourites =
                DjPlanner.weightedSample(candidates, size) { song ->
                    DjPlanner.favouriteWeight(
                        rank = rank[song.id] ?: mostPlayed.size,
                        liked = song.id in likedIds,
                        fitsHour = song.id in hourIds,
                        artistSkips = song.artists.firstOrNull()?.id?.let { skips[it] } ?: 0,
                    )
                }

            // New songs grow from what was just heard through, else from strong favourites.
            val seeds = (recentKeeps + favourites.map { it.id }).distinct().take(SEEDS)
            val knownIds = candidates.mapTo(HashSet()) { it.id } + likedIds
            val radios = coroutineScope { seeds.map { async { radioOf(it) } }.awaitAll() }
            val discoveries =
                DjPlanner.byConsensus(radios) { it.id }
                    .filterNot { song ->
                        song.id in excluded || song.id in knownIds ||
                            (hideExplicit && song.explicit) || (hideVideos && song.isVideoSong) ||
                            (song.artists.firstOrNull()?.id?.let { (skips[it] ?: 0) >= ARTIST_SKIPS_TO_DROP } ?: false)
                    }.filterNotRecommended(blocked)

            val set =
                DjPlanner.mix(
                    familiar = favourites.map { Pick.Known(it) },
                    discoveries = discoveries.map { Pick.Fresh(it) },
                    familiarShare = share,
                    size = size,
                    perArtist = PER_ARTIST,
                    artistOf = { it.artist },
                )
            synchronized(lock) {
                set.forEach {
                    played += it.id
                    picks[it.id] = it
                }
            }
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
        const val FIRST_BATCH = 12
        const val BATCH = 20
        const val SEEDS = 4
        const val PER_ARTIST = 2
        const val ARTIST_SKIPS_TO_DROP = 2
        const val FAVOURITES_POOL = 150
        const val FAVOURITES_DAYS = 120L
        const val HOUR_POOL = 40
        const val HOUR_DAYS = 60L
        const val RECENT_HOURS = 3L
    }
}
