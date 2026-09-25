package com.metrolist.music.offline

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ln
import kotlin.random.Random

/** Which downloaded songs the flow draws from. */
enum class FlowMode { ALL, LIKED, FORGOTTEN, NEW }

/** Whether the flow leans to what is played most, to what is played least, or keeps a balance. */
enum class FlowCharacter { FAMILIAR, BALANCED, RARE }

/** A downloaded song as the flow sees it. */
data class FlowTrack(
    val id: String,
    val artistKey: String?,
    val liked: Boolean,
    val downloadedAt: LocalDateTime?,
    val playTime: Long,
    val lastPlayed: LocalDateTime?,
)

/**
 * Builds the offline flow: an endless-feeling order of downloaded songs that favours what the user
 * loves, keeps what was just heard out of the way, and never plays one artist twice in a row when
 * it can be helped.
 */
object FlowPlanner {
    /** Songs not played for this long count as forgotten. */
    private val FORGOTTEN_AFTER: Duration = Duration.ofDays(30)

    /** Songs downloaded this recently count as new. */
    private val NEW_WITHIN: Duration = Duration.ofDays(30)

    /** "New" always offers at least this many of the latest downloads, however old. */
    private const val NEW_MINIMUM = 20

    /** How far ahead to look for a song by another artist to break up a run. */
    private const val ARTIST_WINDOW = 8

    fun candidates(
        tracks: List<FlowTrack>,
        mode: FlowMode,
        now: LocalDateTime,
    ): List<FlowTrack> =
        when (mode) {
            FlowMode.ALL -> tracks
            FlowMode.LIKED -> tracks.filter { it.liked }
            FlowMode.FORGOTTEN ->
                tracks.filter { track ->
                    val last = track.lastPlayed
                    track.playTime > 0 && last != null && last.isBefore(now.minus(FORGOTTEN_AFTER))
                }
            FlowMode.NEW -> {
                val byDownload = tracks.filter { it.downloadedAt != null }.sortedByDescending { it.downloadedAt }
                val recent = byDownload.filter { it.downloadedAt!!.isAfter(now.minus(NEW_WITHIN)) }
                if (recent.size >= NEW_MINIMUM) recent else byDownload.take(NEW_MINIMUM)
            }
        }

    fun order(
        tracks: List<FlowTrack>,
        character: FlowCharacter,
        now: LocalDateTime,
        random: Random = Random.Default,
    ): List<FlowTrack> {
        if (tracks.size < 2) return tracks
        val maxPlay = tracks.maxOf { it.playTime }
        // Weighted shuffle: each song draws a key from an exponential with its weight as the rate,
        // so heavier songs tend to come first while every song keeps a chance anywhere.
        val drawn =
            tracks.sortedBy { track ->
                val weight = weight(track, character, familiarity(track.playTime, maxPlay), now)
                -ln(1.0 - random.nextDouble()) / weight
            }
        return spreadArtists(drawn)
    }

    /** 0 for a song never played, 1 for the most played one, on a log scale so a few hits don't flatten the rest. */
    internal fun familiarity(
        playTime: Long,
        maxPlay: Long,
    ): Double = if (maxPlay <= 0 || playTime <= 0) 0.0 else ln(1.0 + playTime) / ln(1.0 + maxPlay)

    internal fun weight(
        track: FlowTrack,
        character: FlowCharacter,
        familiarity: Double,
        now: LocalDateTime,
    ): Double {
        var weight =
            when (character) {
                FlowCharacter.FAMILIAR -> 0.15 + 3.0 * familiarity
                FlowCharacter.BALANCED -> 1.0 + familiarity
                FlowCharacter.RARE -> 0.15 + 3.0 * (1.0 - familiarity)
            }
        if (track.liked) weight *= 1.3
        val last = track.lastPlayed
        if (last != null) {
            val since = Duration.between(last, now)
            weight *=
                when {
                    since < Duration.ofHours(6) -> 0.2
                    since < Duration.ofHours(24) -> 0.6
                    else -> 1.0
                }
        }
        return weight
    }

    /** Swaps in a nearby song by someone else whenever the same artist would play twice in a row. */
    internal fun spreadArtists(tracks: List<FlowTrack>): List<FlowTrack> {
        val result = tracks.toMutableList()
        for (i in 1 until result.size) {
            val previous = result[i - 1].artistKey ?: continue
            if (result[i].artistKey != previous) continue
            val swap = (i + 1 until minOf(result.size, i + 1 + ARTIST_WINDOW)).firstOrNull { result[it].artistKey != previous } ?: continue
            result[i] = result[swap].also { result[swap] = result[i] }
        }
        return result
    }
}
