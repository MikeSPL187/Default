package com.metrolist.music.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class M3UParserTest {
    private fun parse(vararg lines: String) = BackupRestoreViewModel.parseM3U(lines.toList())

    @Test
    fun `exported playlists keep ids, titles, artists and order`() {
        val songs =
            parse(
                "#EXTM3U",
                "#EXTINF:215,Artist A;Artist B - First",
                "https://youtube.com/watch?v=abcdefghijk",
                "#EXTINF:180,Artist C - Second",
                "https://music.youtube.com/watch?v=ABCDEFGHIJ_&list=RD",
            )
        assertEquals(listOf("abcdefghijk", "ABCDEFGHIJ_"), songs.map { it.song.id })
        assertEquals(listOf("First", "Second"), songs.map { it.song.title })
        assertEquals(listOf("Artist A", "Artist B"), songs.first().artists.map { it.name })
    }

    @Test
    fun `ytm tag wins over a local file path`() {
        val songs = parse("#EXTINF:100,Band - Song", "#YTM:zyxwvutsrqp", "/storage/Music/song.mp3")
        assertEquals("zyxwvutsrqp", songs.single().song.id)
    }

    @Test
    fun `entries without an id are kept for search`() {
        val songs = parse("#EXTINF:100,Band - Song", "/storage/Music/song.mp3", "short.youtu.be/notanid")
        assertEquals(listOf("", ""), songs.map { it.song.id })
        assertEquals("Song", songs.first().song.title)
    }

    @Test
    fun `short links and files without header are accepted`() {
        val songs = parse("https://youtu.be/abcdefghijk?si=x")
        assertEquals("abcdefghijk", songs.single().song.id)
    }
}
