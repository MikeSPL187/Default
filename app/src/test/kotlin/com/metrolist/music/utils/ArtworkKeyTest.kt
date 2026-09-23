package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtworkKeyTest {
    @Test
    fun `resized googleusercontent covers share one key`() {
        val base = "https://lh3.googleusercontent.com/abc123"
        assertEquals(artworkKey("$base=w60-h60-l90-rj"), artworkKey("$base=w1000-h1000-p-l90-rj"))
        assertEquals(artworkKey("$base=w60-h60-l90-rj"), artworkKey("$base=s544"))
    }

    @Test
    fun `ggpht and ytimg covers are keyed without size or query`() {
        assertEquals(
            artworkKey("https://yt3.ggpht.com/xyz=s88-c-k"),
            artworkKey("https://yt3.ggpht.com/xyz=w500-h500-p-l90-rj"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            artworkKey("https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=1"),
        )
        assertNotEquals(artworkKey("https://lh3.googleusercontent.com/a=w1-h1"), artworkKey("https://lh3.googleusercontent.com/b=w1-h1"))
    }
}
