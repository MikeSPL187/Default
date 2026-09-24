package com.metrolist.music.playback

import kotlin.random.Random

/**
 * Extends an existing shuffle traversal with the items appended after it.
 *
 * Everything up to and including [currentIndex] keeps its place, so played songs are not
 * replayed and "previous" still works. Upcoming songs keep their relative order, and the new
 * indices (`previousOrder.size until totalCount`) are mixed in at random spots after the
 * current song.
 */
fun extendShuffleOrder(
    previousOrder: List<Int>,
    currentIndex: Int,
    totalCount: Int,
    random: Random = Random.Default,
): IntArray {
    val currentPosition = previousOrder.indexOf(currentIndex)
    val played = if (currentPosition >= 0) previousOrder.subList(0, currentPosition + 1) else emptyList()
    val upcoming = previousOrder.drop(played.size).toMutableList()
    for (newIndex in previousOrder.size until totalCount) {
        upcoming.add(random.nextInt(upcoming.size + 1), newIndex)
    }
    return (played + upcoming).toIntArray()
}
