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
     * Orders a set: [familiar] and [discoveries] in the given share, no artist again within
     * [ARTIST_SPACING] songs when another is at hand (counting [recentArtists], the end of what
     * already plays), and no artist more than [perArtist] times. Among the songs that keep the
     * artists apart, one that [follows] the song before it comes first, so a new find plays right
     * after the favourite that led to it instead of anywhere in the set.
     */
    fun <T> mix(
        familiar: List<T>,
        discoveries: List<T>,
        familiarShare: Double,
        size: Int,
        perArtist: Int = Int.MAX_VALUE,
        recentArtists: List<String> = emptyList(),
        follows: (previous: T, next: T) -> Boolean = { _, _ -> false },
        artistOf: (T) -> String?,
    ): List<T> {
        val known = ArrayDeque(familiar)
        val fresh = ArrayDeque(discoveries)
        val picked = ArrayList<T>(size)
        val perArtistCount = HashMap<String, Int>()
        val lastArtists = ArrayDeque(recentArtists.takeLast(ARTIST_SPACING))
        var knownTaken = 0
        fun allowed(item: T) = artistOf(item)?.let { (perArtistCount[it] ?: 0) < perArtist } ?: true
        while (picked.size < size) {
            known.removeAll { !allowed(it) }
            fresh.removeAll { !allowed(it) }
            if (known.isEmpty() && fresh.isEmpty()) break
            val previousItem = picked.lastOrNull()
            val knownDue = familiarShare * (picked.size + 1)
            val wantKnown = knownTaken < knownDue
            val preferred = if ((wantKnown && known.isNotEmpty()) || fresh.isEmpty()) known else fresh
            val previousArtist = lastArtists.lastOrNull()
            fun spaced(item: T) = artistOf(item)?.let { it !in lastArtists } ?: true
            fun followsPrevious(item: T) = previousItem != null && follows(previousItem, item)
            fun firstIn(
                source: ArrayDeque<T>,
                fits: (T) -> Boolean,
            ) = source.indices.take(LOOK_AHEAD).firstOrNull { fits(source[it]) }?.let { source to it }
            // A new find that follows the song before it may cut in ahead of a favourite that is
            // due, by one song at most, so the share stays near what was asked.
            val (source, index) =
                firstIn(preferred) { spaced(it) && followsPrevious(it) }
                    ?: firstIn(fresh) { spaced(it) && followsPrevious(it) }?.takeIf { knownDue - knownTaken <= 1 }
                    ?: firstIn(preferred) { spaced(it) }
                    ?: firstIn(preferred) { previousArtist == null || artistOf(it) != previousArtist }
                    ?: (preferred to 0)
            val item = source.removeAt(index)
            picked += item
            artistOf(item)?.let {
                perArtistCount[it] = (perArtistCount[it] ?: 0) + 1
                lastArtists.addLast(it)
                while (lastArtists.size > ARTIST_SPACING) lastArtists.removeFirst()
            }
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
     * How strongly a favourite is wanted now: the more it was played the better, a liked song, one
     * usually played at this hour or one not heard for a month more so, and far less once its
     * artist was skipped tonight.
     */
    fun favouriteWeight(
        rank: Int,
        liked: Boolean,
        fitsHour: Boolean,
        artistSkips: Double,
        throwback: Boolean = false,
    ): Double {
        val base = 1.0 / (1 + rank * RANK_DECAY)
        val bonus = 1.0 + (if (liked) LIKED_BONUS else 0.0) + (if (fitsHour) HOUR_BONUS else 0.0) + (if (throwback) THROWBACK_BONUS else 0.0)
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
     * For each song of the [radios], the seed whose radio lists it earliest: the song that led to
     * it, to follow in the set and to credit or blame for it. [seeds] and [radios] go together.
     */
    fun <T> leadSeeds(
        seeds: List<String>,
        radios: List<List<T>>,
        idOf: (T) -> String,
    ): Map<String, String> {
        val best = HashMap<String, Pair<String, Int>>()
        seeds.zip(radios).forEach { (seed, radio) ->
            radio.forEachIndexed { position, item ->
                val id = idOf(item)
                val current = best[id]
                if (current == null || position < current.second) best[id] = seed to position
            }
        }
        return best.mapValues { it.value.first }
    }

    /**
     * How much a leave says against a song's artist, from 0 for a song heard on: a new song left in
     * its first seconds says the most, a favourite left early mostly says "not now".
     */
    fun skipWeight(
        playedMs: Long,
        durationMs: Long,
        familiar: Boolean,
    ): Double {
        if (!isSkip(playedMs, durationMs)) return 0.0
        val weight = if (playedMs < EARLY_SKIP_MS) 1.0 else LATE_SKIP_WEIGHT
        return if (familiar) weight * FAMILIAR_SKIP_WEIGHT else weight
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

    /** Skips in a row after which the songs lined up no longer fit and are chosen again. */
    const val SKIPS_TO_RETUNE = 2

    /** Songs between two artists' turns, where the songs at hand allow it. */
    const val ARTIST_SPACING = 3

    private const val LOOK_AHEAD = 6
    private const val RANK_DECAY = 0.04
    private const val LIKED_BONUS = 0.6
    private const val HOUR_BONUS = 0.8
    private const val THROWBACK_BONUS = 0.5
    private const val SKIP_PENALTY = 0.3
    private const val POSITION_DECAY = 0.15
    private const val SHARE_STEP = 0.06
    private const val SKIP_MS = 30_000L
    private const val EARLY_SKIP_MS = 10_000L
    private const val LATE_SKIP_WEIGHT = 0.6
    private const val FAMILIAR_SKIP_WEIGHT = 0.5
}
