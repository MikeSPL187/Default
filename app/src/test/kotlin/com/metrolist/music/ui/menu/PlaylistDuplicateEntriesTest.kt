package com.metrolist.music.ui.menu

import com.metrolist.music.db.entities.PlaylistSongMap
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDuplicateEntriesTest {
    private fun entry(id: Int, songId: String, position: Int) =
        PlaylistSongMap(id = id, playlistId = "p", songId = songId, position = position)

    @Test
    fun `first occurrence by position is kept`() {
        val entries = listOf(entry(1, "b", 2), entry(2, "a", 0), entry(3, "a", 1), entry(4, "b", 3), entry(5, "c", 4))
        assertEquals(listOf(3, 4), duplicatePlaylistEntries(entries).map { it.id })
    }

    @Test
    fun `no duplicates yields nothing`() {
        assertEquals(emptyList<PlaylistSongMap>(), duplicatePlaylistEntries(listOf(entry(1, "a", 0), entry(2, "b", 1))))
    }
}
