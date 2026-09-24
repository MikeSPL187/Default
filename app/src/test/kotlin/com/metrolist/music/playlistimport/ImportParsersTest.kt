package com.metrolist.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParsersTest {
    @Test
    fun `recognises Yandex Music and Spotify links in their usual shapes`() {
        assertEquals(
            ImportLink.YandexUserPlaylist("music-blog", "2011"),
            parseImportLink("https://music.yandex.ru/users/music-blog/playlists/2011?utm_source=web&utm_medium=copy_link"),
        )
        assertEquals(ImportLink.YandexUserPlaylist("ivan.petrov", "3"), parseImportLink("music.yandex.com/users/ivan.petrov/playlists/3"))
        assertEquals(
            ImportLink.YandexPlaylist("lk.4fd6a9ed-5d3e-4c0b-9f76-9c2d4a1b2c3d"),
            parseImportLink("https://music.yandex.ru/playlists/lk.4fd6a9ed-5d3e-4c0b-9f76-9c2d4a1b2c3d?utm_medium=copy_link"),
        )
        assertEquals(ImportLink.YandexAlbum("123456"), parseImportLink("https://music.yandex.by/album/123456/track/7890"))
        assertEquals(ImportLink.SpotifyPlaylist("37i9dQZF1DXcBWIGoYBM5M"), parseImportLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"))
        assertEquals(ImportLink.SpotifyPlaylist("37i9dQZF1DXcBWIGoYBM5M"), parseImportLink("https://open.spotify.com/intl-ru/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals(ImportLink.SpotifyAlbum("4aawyAB9vmqN3uQ7FjRGTy"), parseImportLink("spotify:album:4aawyAB9vmqN3uQ7FjRGTy"))
    }

    @Test
    fun `other links are not taken`() {
        listOf("", "hello", "https://music.youtube.com/playlist?list=PL123", "https://music.yandex.ru/artist/123", "https://open.spotify.com/track/abc")
            .forEach { assertNull(it, parseImportLink(it)) }
    }

    @Test
    fun `Yandex playlist keeps full tracks and returns ids of bare ones`() {
        val body =
            """
            {"result":{"title":"Лето","cover":{"uri":"avatars.yandex.net/get-music-content/1/abc/%%"},
              "tracks":[
                {"id":1,"track":{"id":"1","title":"Песня","version":"Remix","durationMs":215000,
                  "artists":[{"name":"Би-2"},{"name":"Oxxxymiron"}],"albums":[{"title":"Альбом"}]}},
                {"id":2,"track":{"id":"2","title":"Second","artists":[]}},
                {"id":3}
              ]}}
            """.trimIndent()

        val (playlist, missing) = parseYandexPlaylist(body)

        assertEquals("Лето", playlist.title)
        assertEquals("https://avatars.yandex.net/get-music-content/1/abc/400x400", playlist.coverUrl)
        assertEquals(ImportedTrack("Песня (Remix)", listOf("Би-2", "Oxxxymiron"), 215, "Альбом"), playlist.tracks[0])
        assertEquals("Second", playlist.tracks[1].title)
        assertEquals(listOf("3"), missing)
    }

    @Test
    fun `Yandex album lists every disc in order`() {
        val body =
            """
            {"result":{"title":"Album","artists":[{"name":"Artist"}],"coverUri":"avatars.yandex.net/x/%%",
              "volumes":[[{"title":"One","artists":[{"name":"Artist"}]}],[{"title":"Two","artists":[{"name":"Artist"}]}]]}}
            """.trimIndent()

        val album = parseYandexAlbum(body)

        assertEquals("Artist — Album", album.title)
        assertEquals(listOf("One", "Two"), album.tracks.map { it.title })
    }

    @Test
    fun `Spotify embed page gives the track list`() {
        val html =
            """
            <html><body><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{"name":"Chill","coverArt":{"sources":[{"url":"small","width":60},{"url":"big","width":640}]},
              "trackList":[{"title":"Song","subtitle":"Artist One,${" "}Artist Two","duration":200500},{"title":"Other","subtitle":"Solo","duration":1000}]}}}}}}
            </script></body></html>
            """.trimIndent()

        val playlist = parseSpotifyEmbed(html)

        assertEquals("Chill", playlist.title)
        assertEquals("big", playlist.coverUrl)
        assertEquals(ImportedTrack("Song", listOf("Artist One", "Artist Two"), 200), playlist.tracks[0])
        assertFalse(playlist.truncated)
    }

    @Test
    fun `a pasted list takes numbered lines and any dash`() {
        val tracks =
            parseTrackList(
                """
                1. Би-2 — Полковнику никто не пишет
                02) Queen - Bohemian Rhapsody

                Daft Punk feat. Pharrell Williams – Get Lucky
                Just a title
                7 Rings
                """.trimIndent(),
            )

        assertEquals(5, tracks.size)
        assertEquals(ImportedTrack("Полковнику никто не пишет", listOf("Би-2")), tracks[0])
        assertEquals(ImportedTrack("Bohemian Rhapsody", listOf("Queen")), tracks[1])
        assertEquals(listOf("Daft Punk", "Pharrell Williams"), tracks[2].artists)
        assertEquals(ImportedTrack("Just a title", emptyList()), tracks[3])
        assertEquals("7 Rings", tracks[4].title)
        assertTrue(parseTrackList("  \n ").isEmpty())
    }
}
