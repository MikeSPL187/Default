package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleOrderTest {
    @Test
    fun `keeps history and upcoming order while mixing in new songs after the current one`() {
        val previous = listOf(3, 0, 4, 1, 2)
        repeat(50) { seed ->
            val order = extendShuffleOrder(previous, currentIndex = 4, totalCount = 8, random = Random(seed)).toList()

            assertEquals((0 until 8).toSet(), order.toSet())
            assertEquals(8, order.size)
            assertEquals(listOf(3, 0, 4), order.take(3))
            assertEquals(listOf(1, 2), order.filter { it in listOf(1, 2) })
            assertTrue(listOf(5, 6, 7).all { order.indexOf(it) > order.indexOf(4) })
        }
    }

    @Test
    fun `without new songs the order is unchanged`() {
        val previous = listOf(2, 0, 1)
        assertEquals(previous, extendShuffleOrder(previous, currentIndex = 0, totalCount = 3).toList())
    }

    @Test
    fun `unknown current index still yields a full permutation`() {
        val order = extendShuffleOrder(listOf(1, 0), currentIndex = 9, totalCount = 4, random = Random(1)).toList()
        assertEquals((0 until 4).toSet(), order.toSet())
    }
}
