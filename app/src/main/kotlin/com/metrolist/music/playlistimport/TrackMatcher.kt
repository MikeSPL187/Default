package com.metrolist.music.playlistimport

import java.text.Normalizer
import kotlin.math.abs

/** A YouTube Music search result, reduced to what matching needs. */
data class MatchCandidate(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationSec: Int?,
)

/**
 * Scores how likely a search result is the imported track. Search puts popular uploads first, so
 * taking the top result picks karaoke, sped-up and cover versions or a different song with the
 * same name; comparing title, artist and length avoids that.
 */
object TrackMatcher {
    /** Below this a result is not taken: a missing track is better than a wrong one. */
    const val ACCEPT_SCORE = 0.62

    /** Words that mark a different recording; a result may carry one only if the original does. */
    private val VERSION_MARKERS =
        listOf(
            "remix", "live", "acoustic", "instrumental", "karaoke", "cover", "sped up", "speed up", "slowed",
            "nightcore", "8d", "reverb", "minus", "минус", "караоке", "ремикс", "кавер", "концерт",
        )

    private val FEATURING = Regex("""[(\[]?\s*\b(feat|ft|featuring|при уч|при участии)\b\.?[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("""[(\[][^)\]]*[)\]]""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val DIACRITICS = Regex("""\p{Mn}+""")

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace('ё', 'е')
            .replace(FEATURING, " ")
            .replace(NON_WORD, " ")
            .trim()

    private fun words(text: String): Set<String> = normalize(text).split(' ').filter(String::isNotEmpty).toSet()

    /** The title without anything in brackets, for comparing the song itself. */
    private fun core(title: String): Set<String> = words(title.replace(BRACKETS, " ")).ifEmpty { words(title) }

    private fun overlap(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else 2.0 * a.intersect(b).size / (a.size + b.size)

    private fun markers(text: String): Set<String> {
        val normalized = " ${normalize(text)} "
        return VERSION_MARKERS.filterTo(HashSet()) { " $it " in normalized }
    }

    fun score(track: ImportedTrack, candidate: MatchCandidate): Double {
        val trackCore = core(track.title)
        val candidateCore = core(candidate.title)
        var title = overlap(trackCore, candidateCore)
        // A title the other service shortened or lengthened by a word ("Song" and "Song Intro").
        val (smaller, larger) = if (trackCore.size <= candidateCore.size) trackCore to candidateCore else candidateCore to trackCore
        if (smaller.isNotEmpty() && larger.containsAll(smaller) && larger.size - smaller.size <= 1) {
            title = maxOf(title, 0.85)
        }

        val candidateArtists = words(candidate.artists.joinToString(" ") + " " + candidate.title)
        val artist =
            when {
                track.artists.isEmpty() -> 0.5
                track.artists.any { name -> words(name).let { it.isNotEmpty() && candidateArtists.containsAll(it) } } -> 1.0
                track.artists.any { name -> overlap(words(name), candidateArtists) >= 0.5 } -> 0.6
                else -> 0.0
            }

        val duration =
            if (track.durationSec == null || candidate.durationSec == null) {
                0.5
            } else {
                when (abs(track.durationSec - candidate.durationSec)) {
                    in 0..3 -> 1.0
                    in 4..10 -> 0.7
                    in 11..30 -> 0.3
                    else -> 0.0
                }
            }

        // A result may not be a different version than asked for, nor lack the version asked for.
        val trackVersions = markers(track.title)
        val candidateVersions = markers(candidate.title)
        val unexpected = (candidateVersions - trackVersions).size
        val missing = (trackVersions - candidateVersions).size
        return 0.5 * title + 0.35 * artist + 0.15 * duration - 0.3 * unexpected - 0.2 * missing
    }

    /**
     * The best result that is good enough, or null. With [allowSwap], for a pasted list whose lines
     * may read "title — artist", the two parts are also tried the other way round.
     */
    fun pick(
        track: ImportedTrack,
        candidates: List<MatchCandidate>,
        allowSwap: Boolean = false,
    ): MatchCandidate? {
        val swapped =
            track.artists.singleOrNull()?.takeIf { allowSwap }
                ?.let { ImportedTrack(title = it, artists = listOf(track.title), durationSec = track.durationSec) }
        return candidates
            .map { candidate -> candidate to maxOf(score(track, candidate), swapped?.let { score(it, candidate) } ?: 0.0) }
            .filter { it.second >= ACCEPT_SCORE }
            .maxByOrNull { it.second }
            ?.first
    }
}
