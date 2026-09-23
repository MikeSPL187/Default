package com.metrolist.music.utils

import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.Queue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotRecommendedTest {
    private fun song(id: String, artistId: String = "artist-$id") =
        MediaMetadata(
            id = id,
            title = id,
            artists = listOf(MediaMetadata.Artist(id = artistId, name = artistId)),
            duration = 100,
        ).toMediaItem()

    @Test
    fun `blocks hidden songs and songs by hidden artists`() {
        val hidden = NotRecommended(songIds = setOf("a"), artistIds = setOf("band"))
        assertTrue(hidden.blocks("a", listOf("other")))
        assertTrue(hidden.blocks("b", listOf(null, "band")))
        assertFalse(hidden.blocks("c", listOf("other", null)))
    }

    @Test
    fun `list filter drops hidden songs and artists`() {
        val items = listOf(song("a"), song("b", "band"), song("c"))
        val filtered = items.filterNotRecommended(NotRecommended(setOf("a"), setOf("band")))
        assertEquals(listOf("c"), filtered.map { it.mediaId })
    }

    @Test
    fun `queue filter keeps the picked song and moves its index`() {
        val status = Queue.Status(
            title = null,
            items = listOf(song("a"), song("b"), song("picked"), song("c")),
            mediaItemIndex = 2,
        )
        val filtered = status.filterNotRecommended(NotRecommended(songIds = setOf("a", "picked", "c")))
        assertEquals(listOf("b", "picked"), filtered.items.map { it.mediaId })
        assertEquals(1, filtered.mediaItemIndex)
    }

    @Test
    fun `empty list changes nothing`() {
        val status = Queue.Status(title = null, items = listOf(song("a")), mediaItemIndex = 0)
        assertEquals(status, status.filterNotRecommended(NotRecommended()))
    }
}
