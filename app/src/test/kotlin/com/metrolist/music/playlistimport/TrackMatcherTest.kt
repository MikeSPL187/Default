package com.metrolist.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatcherTest {
    private fun candidate(id: String, title: String, vararg artists: String, duration: Int? = null) =
        MatchCandidate(id, title, artists.toList(), duration)

    @Test
    fun `picks the right recording over a more popular karaoke or sped up one`() {
        val track = ImportedTrack("Blinding Lights", listOf("The Weeknd"), durationSec = 200)
        val results =
            listOf(
                candidate("karaoke", "Blinding Lights (Karaoke Version)", "Sing King", duration = 201),
                candidate("sped", "Blinding Lights (Sped Up)", "The Weeknd", duration = 150),
                candidate("real", "Blinding Lights", "The Weeknd", duration = 200),
            )

        assertEquals("real", TrackMatcher.pick(track, results)?.id)
    }

    @Test
    fun `a remix is taken when the original asked for one`() {
        val track = ImportedTrack("Песня (Remix)", listOf("Би-2"))
        val results = listOf(candidate("orig", "Песня", "Би-2"), candidate("remix", "Песня (Remix)", "Би-2"))

        assertEquals("remix", TrackMatcher.pick(track, results)?.id)
    }

    @Test
    fun `ignores featuring, case, punctuation and yo`() {
        val track = ImportedTrack("Get Lucky (feat. Pharrell Williams)", listOf("Daft Punk"), durationSec = 369)
        val results = listOf(candidate("a", "GET LUCKY", "Daft Punk", "Pharrell Williams", duration = 368))

        assertEquals("a", TrackMatcher.pick(track, results)?.id)
        assertEquals(TrackMatcher.normalize("Ёлка"), TrackMatcher.normalize("елка"))
    }

    @Test
    fun `another song with the same name by someone else is left out`() {
        val track = ImportedTrack("Hello", listOf("Adele"), durationSec = 295)
        val results = listOf(candidate("x", "Hello", "Lionel Richie", duration = 247))

        assertNull(TrackMatcher.pick(track, results))
    }

    @Test
    fun `a pasted line written title first is still found`() {
        val track = ImportedTrack(title = "Queen", artists = listOf("Bohemian Rhapsody"))
        val results = listOf(candidate("q", "Bohemian Rhapsody", "Queen"))

        assertNull(TrackMatcher.pick(track, results))
        assertEquals("q", TrackMatcher.pick(track, results, allowSwap = true)?.id)
    }
}
