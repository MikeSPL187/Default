package com.metrolist.music.video

import android.util.LruCache
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.VideoCounterpart
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playlistimport.ImportedTrack
import com.metrolist.music.playlistimport.MatchCandidate
import com.metrolist.music.playlistimport.TrackMatcher
import kotlin.math.abs

/**
 * The music video shown for a song, and where in it each moment of the song falls: a video often
 * opens with a scene the song does not have, so its time can run ahead of the song's.
 */
data class VideoClip(
    val videoId: String,
    val segments: List<VideoCounterpart.Segment> = emptyList(),
) {
    /** The video's position for the song's position [songMs]. */
    fun videoPositionFor(songMs: Long): Long {
        if (segments.isEmpty()) return songMs
        // Between two mapped stretches, the nearer one decides.
        val segment =
            segments.minBy { segment ->
                when {
                    songMs < segment.songStartMs -> segment.songStartMs - songMs
                    songMs > segment.songStartMs + segment.durationMs -> songMs - segment.songStartMs - segment.durationMs
                    else -> 0L
                }
            }
        return (songMs - segment.songStartMs + segment.videoStartMs).coerceAtLeast(0L)
    }
}

/** Finds the music video of a song, once per song. */
object VideoClips {
    private val found = LruCache<String, Found>(300)

    /** A lookup that ended, found or not; failed lookups are not kept, so they are tried again. */
    private class Found(val clip: VideoClip?)

    /**
     * The video for [song]: its own picture when the song is a music video, else the video YouTube
     * Music pairs with it, else a search result that is plainly the same recording.
     */
    suspend fun find(song: MediaMetadata): VideoClip? {
        found.get(song.id)?.let { return it.clip }
        // Episodes and the user's own uploads have no music video.
        if (song.isEpisode || song.uploadEntityId != null) return null.also { found.put(song.id, Found(null)) }
        if (song.isVideoSong) return VideoClip(song.id).also { found.put(song.id, Found(it)) }

        val paired = YouTube.videoCounterpart(song.id).getOrElse { return null }
        val clip = paired?.let { VideoClip(it.videoId, it.segments) } ?: searchSameRecording(song) ?: return null.also {
            found.put(song.id, Found(null))
        }
        found.put(song.id, Found(clip))
        return clip
    }

    /**
     * A search result is only trusted when it is the same song by the same artist and lasts as
     * long to within a second: a longer cut would run out of time with the song.
     */
    private suspend fun searchSameRecording(song: MediaMetadata): VideoClip? {
        if (song.duration <= 0) return null
        val query = (song.artists.take(2).map { it.name } + song.title).joinToString(" ")
        val results = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items ?: return null
        val track = ImportedTrack(title = song.title, artists = song.artists.map { it.name }, durationSec = song.duration)
        return results
            .filterIsInstance<SongItem>()
            .take(SEARCH_CANDIDATES)
            .filter { it.id != song.id && it.duration?.let { length -> abs(length - song.duration) <= MAX_LENGTH_DIFFERENCE_S } == true }
            .map { it to TrackMatcher.score(track, MatchCandidate(it.id, it.title, it.artists.map { artist -> artist.name }, it.duration)) }
            .filter { it.second >= TrackMatcher.ACCEPT_SCORE }
            .maxByOrNull { it.second }
            ?.let { VideoClip(it.first.id) }
    }

    private const val SEARCH_CANDIDATES = 8
    private const val MAX_LENGTH_DIFFERENCE_S = 1
}
