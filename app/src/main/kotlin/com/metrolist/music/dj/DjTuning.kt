package com.metrolist.music.dj

import kotlinx.coroutines.flow.MutableStateFlow

/** How much of a set the DJ gives to favourites, and the bounds its learning keeps to. */
enum class DjMode(
    val base: Double,
    val min: Double,
    val max: Double,
) {
    FAVOURITES(0.8, 0.65, 0.95),
    MIXED(DjPlanner.BALANCED, DjPlanner.MIN_FAMILIAR, DjPlanner.MAX_FAMILIAR),
    DISCOVER(0.25, 0.1, 0.4),
}

/** A YouTube Music mood the DJ draws its new songs from. */
data class DjMood(
    val title: String,
    val params: String,
)

/**
 * The mood chosen for this session, shared by the home chips and the DJ. It is not saved: a mood
 * picked last night should not colour tomorrow morning.
 */
object DjSession {
    val mood = MutableStateFlow<DjMood?>(null)
}
