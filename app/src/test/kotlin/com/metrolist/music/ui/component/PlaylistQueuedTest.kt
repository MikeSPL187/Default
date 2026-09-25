package com.metrolist.music.ui.component

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistQueuedTest {
    private val songs = setOf("a", "b")

    private fun queued(
        title: String? = "Liked",
        current: String? = "a",
        state: Int = Player.STATE_READY,
    ) = isPlaylistQueued(title, "Liked", current, state) { it in songs }

    @Test
    fun `the playlist playing one of its songs is queued`() = assertTrue(queued())

    @Test
    fun `a closed player keeps the title but is not queued`() = assertFalse(queued(current = null))

    @Test
    fun `a queue that played out starts over`() = assertFalse(queued(state = Player.STATE_ENDED))

    @Test
    fun `another queue or a foreign song is not this playlist`() {
        assertFalse(queued(title = "Other"))
        assertFalse(queued(current = "z"))
        assertFalse(queued(title = null))
    }
}
