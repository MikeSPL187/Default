package com.metrolist.music.playback.queues

import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueueStatusFilterTest {
    private fun song(id: String, explicit: Boolean = false) =
        MediaMetadata(id = id, title = id, artists = emptyList(), duration = 100, explicit = explicit).toMediaItem()

    private fun status(index: Int, position: Long = 0L) =
        Queue.Status(
            title = null,
            items = listOf(song("a", explicit = true), song("b"), song("c", explicit = true), song("d")),
            mediaItemIndex = index,
            position = position,
        )

    @Test
    fun `index follows the picked song after earlier items are removed`() {
        val filtered = status(index = 3, position = 5_000).filterExplicit()
        assertEquals(listOf("b", "d"), filtered.items.map { it.mediaId })
        assertEquals("d", filtered.items[filtered.mediaItemIndex].mediaId)
        assertEquals(5_000, filtered.position)
    }

    @Test
    fun `removed pick starts the next remaining song from the beginning`() {
        val filtered = status(index = 2, position = 5_000).filterExplicit()
        assertEquals("d", filtered.items[filtered.mediaItemIndex].mediaId)
        assertEquals(0, filtered.position)
    }

    @Test
    fun `removed last pick stays inside the list`() {
        val status = Queue.Status(null, listOf(song("a"), song("b", explicit = true)), mediaItemIndex = 1)
        val filtered = status.filterExplicit()
        assertEquals(0, filtered.mediaItemIndex)
    }

    @Test
    fun `nothing removed returns the same status`() {
        val status = Queue.Status(null, listOf(song("a"), song("b")), mediaItemIndex = 1)
        assertSame(status, status.filterExplicit())
        assertSame(status, status.filterExplicit(enabled = false))
    }
}
