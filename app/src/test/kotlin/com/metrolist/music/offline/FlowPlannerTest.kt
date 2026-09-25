package com.metrolist.music.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class FlowPlannerTest {
    private val now = LocalDateTime.of(2026, 9, 25, 12, 0)

    private fun track(
        id: String,
        artist: String? = id,
        liked: Boolean = false,
        downloadedDaysAgo: Long? = 100,
        playTime: Long = 0,
        lastPlayedDaysAgo: Long? = null,
    ) = FlowTrack(
        id = id,
        artistKey = artist,
        liked = liked,
        downloadedAt = downloadedDaysAgo?.let { now.minusDays(it) },
        playTime = playTime,
        lastPlayed = lastPlayedDaysAgo?.let { now.minusDays(it) },
    )

    @Test
    fun `modes pick the right songs`() {
        val tracks =
            listOf(
                track("liked", liked = true),
                track("forgotten", playTime = 60_000, lastPlayedDaysAgo = 45),
                track("recent", playTime = 60_000, lastPlayedDaysAgo = 2),
                track("never"),
            )
        assertEquals(4, FlowPlanner.candidates(tracks, FlowMode.ALL, now).size)
        assertEquals(listOf("liked"), FlowPlanner.candidates(tracks, FlowMode.LIKED, now).map { it.id })
        assertEquals(listOf("forgotten"), FlowPlanner.candidates(tracks, FlowMode.FORGOTTEN, now).map { it.id })
    }

    @Test
    fun `new keeps the recent downloads, or at least the latest few`() {
        val many = (1..25).map { track("n$it", downloadedDaysAgo = it.toLong()) } + track("old", downloadedDaysAgo = 400)
        val new = FlowPlanner.candidates(many, FlowMode.NEW, now).map { it.id }
        assertEquals(25, new.size)
        assertTrue("old" !in new)

        val few = listOf(track("a", downloadedDaysAgo = 1), track("b", downloadedDaysAgo = 400), track("c", downloadedDaysAgo = null))
        assertEquals(listOf("a", "b"), FlowPlanner.candidates(few, FlowMode.NEW, now).map { it.id })
    }

    @Test
    fun `familiar puts the most played songs first, rare the least`() {
        val tracks = (0 until 40).map { track("t$it", playTime = if (it < 5) 10_000_000L else 0L) }
        fun topFiveHits(character: FlowCharacter): Double =
            (0 until 200).map { seed ->
                FlowPlanner.order(tracks, character, now, Random(seed)).take(5).count { it.playTime > 0 }
            }.average()
        assertTrue(topFiveHits(FlowCharacter.FAMILIAR) > 2.5)
        assertTrue(topFiveHits(FlowCharacter.RARE) < 0.5)
    }

    @Test
    fun `a song just heard is held back`() {
        val tracks = listOf(track("just", playTime = 60_000, lastPlayedDaysAgo = 0)) + (1..9).map { track("t$it", playTime = 60_000, lastPlayedDaysAgo = 10) }
        val firstPlaces = (0 until 300).count { FlowPlanner.order(tracks, FlowCharacter.BALANCED, now, Random(it)).first().id == "just" }
        assertTrue(firstPlaces < 20)
    }

    @Test
    fun `the same artist does not play twice in a row when another is near`() {
        val tracks = listOf(track("a1", "A"), track("a2", "A"), track("b1", "B"), track("a3", "A"), track("b2", "B"))
        val spread = FlowPlanner.spreadArtists(tracks)
        assertEquals(tracks.toSet(), spread.toSet())
        assertEquals(listOf("A", "B", "A", "B", "A"), spread.map { it.artistKey })
    }

    @Test
    fun `every song stays in the order`() {
        val tracks = (0 until 30).map { track("t$it", artist = "a${it % 3}", playTime = it * 1000L) }
        val ordered = FlowPlanner.order(tracks, FlowCharacter.BALANCED, now, Random(1))
        assertEquals(tracks.map { it.id }.toSet(), ordered.map { it.id }.toSet())
        assertEquals(tracks.size, ordered.size)
    }
}
