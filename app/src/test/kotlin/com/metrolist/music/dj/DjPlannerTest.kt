package com.metrolist.music.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DjPlannerTest {
    private data class S(val id: String, val artist: String)

    @Test
    fun `half familiar half new`() {
        val known = (1..10).map { S("k$it", "ka$it") }
        val fresh = (1..10).map { S("n$it", "na$it") }
        val set = DjPlanner.mix(known, fresh, DjPlanner.BALANCED, 10) { it.artist }
        assertEquals(10, set.size)
        assertEquals(5, set.count { it.id.startsWith("k") })
    }

    @Test
    fun `fills from the other side when one runs out`() {
        val set = DjPlanner.mix(listOf(S("k1", "a")), (1..5).map { S("n$it", "b$it") }, 0.8, 4) { it.artist }
        assertEquals(4, set.size)
        assertTrue(set.any { it.id == "k1" })
    }

    @Test
    fun `an artist does not play twice in a row when another is near`() {
        val known = listOf(S("k1", "A"), S("k2", "A"), S("k3", "B"))
        val set = DjPlanner.mix(known, emptyList(), 1.0, 3) { it.artist }
        assertEquals(listOf("A", "B", "A"), set.map { it.artist })
    }

    @Test
    fun `nothing to play gives an empty set`() {
        assertTrue(DjPlanner.mix(emptyList<S>(), emptyList(), 0.5, 10) { it.artist }.isEmpty())
    }

    @Test
    fun `no artist takes more than its share of a set`() {
        val known = (1..6).map { S("k$it", "A") } + (1..6).map { S("b$it", "B$it") }
        val set = DjPlanner.mix(known, emptyList(), 1.0, 8, perArtist = 2) { it.artist }
        assertEquals(2, set.count { it.artist == "A" })
        assertEquals(8, set.size)
    }

    @Test
    fun `heavier favourites come back more often`() {
        val random = Random(7)
        var heavy = 0
        repeat(2000) {
            if (DjPlanner.weightedSample(listOf("heavy", "light"), 1, random) { if (it == "heavy") 4.0 else 1.0 }.single() == "heavy") heavy++
        }
        assertTrue("heavy picked $heavy of 2000", heavy in 1450..1750)
    }

    @Test
    fun `a weightless item is never drawn`() {
        assertEquals(listOf("a"), DjPlanner.weightedSample(listOf("a", "b"), 2) { if (it == "a") 1.0 else 0.0 })
    }

    @Test
    fun `a skipped artist weighs far less`() {
        val fresh = DjPlanner.favouriteWeight(rank = 0, liked = false, fitsHour = false, artistSkips = 0)
        val skipped = DjPlanner.favouriteWeight(rank = 0, liked = false, fitsHour = false, artistSkips = 1)
        val loved = DjPlanner.favouriteWeight(rank = 0, liked = true, fitsHour = true, artistSkips = 0)
        assertTrue(skipped < fresh / 2)
        assertTrue(loved > fresh * 2)
    }

    @Test
    fun `songs several radios agree on lead the discoveries`() {
        val radios = listOf(listOf("x", "shared", "y"), listOf("shared", "z"), listOf("w"))
        assertEquals("shared", DjPlanner.byConsensus(radios) { it }.first())
        assertEquals(5, DjPlanner.byConsensus(radios) { it }.size)
    }

    @Test
    fun `the share of favourites follows what is skipped and stays in bounds`() {
        assertEquals(DjPlanner.BALANCED, DjPlanner.adaptShare(0, 0, 0), 1e-9)
        assertTrue(DjPlanner.adaptShare(0, 3, 0) > DjPlanner.BALANCED)
        assertTrue(DjPlanner.adaptShare(3, 0, 0) < DjPlanner.BALANCED)
        assertEquals(DjPlanner.MAX_FAMILIAR, DjPlanner.adaptShare(0, 50, 0), 1e-9)
        assertEquals(DjPlanner.MIN_FAMILIAR, DjPlanner.adaptShare(50, 0, 0), 1e-9)
    }

    @Test
    fun `each mode starts from its own share and keeps to its own bounds`() {
        DjMode.entries.forEach { mode ->
            assertEquals(mode.base, DjPlanner.adaptShare(0, 0, 0, mode), 1e-9)
            assertEquals(mode.max, DjPlanner.adaptShare(0, 50, 0, mode), 1e-9)
            assertEquals(mode.min, DjPlanner.adaptShare(50, 0, 0, mode), 1e-9)
        }
        assertTrue(DjMode.FAVOURITES.min > DjMode.DISCOVER.max)
    }

    @Test
    fun `a skip is an early leave, a keep is a listen to the end`() {
        assertTrue(DjPlanner.isSkip(10_000, 200_000))
        assertTrue(!DjPlanner.isSkip(40_000, 200_000))
        assertTrue(!DjPlanner.isSkip(20_000, 30_000))
        assertTrue(DjPlanner.isKept(170_000, 200_000))
        assertTrue(!DjPlanner.isKept(100_000, 200_000))
        assertTrue(!DjPlanner.isKept(100_000, -1))
    }
}
