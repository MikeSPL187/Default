package com.metrolist.music.dj

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * The decisions of the DJ, kept pure so they can be tested: which favourites to bring back, which
 * new songs to trust, how much of each to play, and in what order.
 */
object DjPlanner {
    /** Half familiar, half new: where the DJ starts before it learns anything. */
    const val BALANCED = 0.5
    const val MIN_FAMILIAR = 0.3
    const val MAX_FAMILIAR = 0.8

    /**
     * Orders a set: [familiar] and [discoveries] in the given share, never one artist twice in a
     * row when another is at hand, and no artist more than [perArtist] times.
     */
    fun <T> mix(
        familiar: List<T>,
        discoveries: List<T>,
        familiarShare: Double,
        size: Int,
        perArtist: Int = Int.MAX_VALUE,
        artistOf: (T) -> String?,
    ): List<T> {
        val known = ArrayDeque(familiar)
        val fresh = ArrayDeque(discoveries)
        val picked = ArrayList<T>(size)
        val perArtistCount = HashMap<String, Int>()
        var knownTaken = 0
        fun allowed(item: T) = artistOf(item)?.let { (perArtistCount[it] ?: 0) < perArtist } ?: true
        while (picked.size < size) {
            known.removeAll { !allowed(it) }
            fresh.removeAll { !allowed(it) }
            if (known.isEmpty() && fresh.isEmpty()) break
            val wantKnown = knownTaken < familiarShare * (picked.size + 1)
            val source = if ((wantKnown && known.isNotEmpty()) || fresh.isEmpty()) known else fresh
            val previous = picked.lastOrNull()?.let(artistOf)
            // Take the first song by someone else within a short look-ahead.
            val index = source.indices.take(LOOK_AHEAD).firstOrNull { previous == null || artistOf(source[it]) != previous } ?: 0
            val item = source.removeAt(index)
            picked += item
            artistOf(item)?.let { perArtistCount[it] = (perArtistCount[it] ?: 0) + 1 }
            if (source === known) knownTaken++
        }
        return picked
    }

    /**
     * Draws [count] items without replacement, each with a chance in proportion to its weight
     * (Efraimidis–Spirakis), so favourites come back often but never in the same order.
     */
    fun <T> weightedSample(
        items: List<T>,
        count: Int,
        random: Random = Random.Default,
        weightOf: (T) -> Double,
    ): List<T> =
        items
            .mapNotNull { item ->
                val weight = weightOf(item)
                if (weight <= 0.0) null else item to ln(random.nextDouble(1e-12, 1.0)) / weight
            }.sortedByDescending { it.second }
            .take(count)
            .map { it.first }

    /**
     * How strongly a favourite is wanted now: the more it was played the better, a liked song or
     * one usually played at this hour more so, and far less once its artist was skipped tonight.
     */
    fun favouriteWeight(
        rank: Int,
        liked: Boolean,
        fitsHour: Boolean,
        artistSkips: Int,
    ): Double {
        val base = 1.0 / (1 + rank * RANK_DECAY)
        val bonus = 1.0 + (if (liked) LIKED_BONUS else 0.0) + (if (fitsHour) HOUR_BONUS else 0.0)
        return base * bonus * SKIP_PENALTY.pow(artistSkips)
    }

    /**
     * Ranks new songs by how many radios of the seeds agree on them, then by how early they came
     * up: a song several of your songs lead to is a safer discovery than one lone suggestion.
     */
    fun <T> byConsensus(
        radios: List<List<T>>,
        idOf: (T) -> String,
    ): List<T> {
        val score = LinkedHashMap<String, Double>()
        val first = HashMap<String, T>()
        radios.forEach { radio ->
            radio.distinctBy(idOf).forEachIndexed { position, item ->
                val id = idOf(item)
                first.putIfAbsent(id, item)
                score[id] = (score[id] ?: 0.0) + 1.0 + 1.0 / (1 + position * POSITION_DECAY)
            }
        }
        return score.entries.sortedByDescending { it.value }.map { first.getValue(it.key) }
    }

    /**
     * The share of favourites for the next set, around what [mode] asks for: new songs listened
     * through ask for more of them, new songs skipped ask for fewer, favourites skipped for more new ones.
     */
    fun adaptShare(
        discoveriesKept: Int,
        discoveriesSkipped: Int,
        favouritesSkipped: Int,
        mode: DjMode = DjMode.MIXED,
    ): Double =
        (mode.base + SHARE_STEP * (discoveriesSkipped - discoveriesKept) - SHARE_STEP / 2 * favouritesSkipped)
            .coerceIn(mode.min, mode.max)

    /** A song left within half a minute, before its middle, was skipped. */
    fun isSkip(
        playedMs: Long,
        durationMs: Long,
    ) = playedMs < SKIP_MS && (durationMs <= 0 || playedMs < durationMs / 2)

    /** A song heard to its last fifth was liked enough to lead the DJ on. */
    fun isKept(
        playedMs: Long,
        durationMs: Long,
    ) = durationMs > 0 && playedMs >= durationMs * 4 / 5

    private const val LOOK_AHEAD = 6
    private const val RANK_DECAY = 0.04
    private const val LIKED_BONUS = 0.6
    private const val HOUR_BONUS = 0.8
    private const val SKIP_PENALTY = 0.3
    private const val POSITION_DECAY = 0.15
    private const val SHARE_STEP = 0.06
    private const val SKIP_MS = 30_000L
}
