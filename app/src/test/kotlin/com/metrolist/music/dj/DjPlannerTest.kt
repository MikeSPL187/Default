package com.metrolist.music.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
