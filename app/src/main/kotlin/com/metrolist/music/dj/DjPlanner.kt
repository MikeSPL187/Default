package com.metrolist.music.dj

/**
 * Orders a DJ set: songs the user loves and songs new to them, in the given share, never one
 * artist twice in a row when another is at hand. Pure, so the mix can be tested.
 */
object DjPlanner {
    /** Half familiar, half new: the default blend of the DJ. */
    const val BALANCED = 0.5

    fun <T> mix(
        familiar: List<T>,
        discoveries: List<T>,
        familiarShare: Double,
        size: Int,
        artistOf: (T) -> String?,
    ): List<T> {
        val known = ArrayDeque(familiar)
        val fresh = ArrayDeque(discoveries)
        val picked = ArrayList<T>(size)
        var knownTaken = 0
        while (picked.size < size && (known.isNotEmpty() || fresh.isNotEmpty())) {
            val wantKnown = knownTaken < familiarShare * (picked.size + 1)
            val source = if ((wantKnown && known.isNotEmpty()) || fresh.isEmpty()) known else fresh
            val previous = picked.lastOrNull()?.let(artistOf)
            // Take the first song by someone else within a short look-ahead.
            val index = source.indices.take(LOOK_AHEAD).firstOrNull { previous == null || artistOf(source[it]) != previous } ?: 0
            picked += source.removeAt(index)
            if (source === known) knownTaken++
        }
        return picked
    }

    private const val LOOK_AHEAD = 6
}
