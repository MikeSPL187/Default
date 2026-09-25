package com.metrolist.music.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WatchUpdaterTest {
    @Test
    fun `the build number comes from the version name or the tag`() {
        assertEquals(84, WatchUpdater.buildNumber("13.7.0-watch.3+b84"))
        assertEquals(85, WatchUpdater.buildNumber("v13.7.0-watch.3-b85"))
        assertNull(WatchUpdater.buildNumber("13.7.0-watch.3"))
        assertNull(WatchUpdater.buildNumber("13.7.0-watch.3+abc1234"))
    }

    @Test
    fun `notes are the list items, or the first line of older releases`() {
        val notes = "Что нового:\n- Обновления в приложении\n- Иконки у настроений\n\nСборка 85 из коммита abc1234."
        assertEquals(listOf("Обновления в приложении", "Иконки у настроений"), WatchUpdater.parseNotes(notes))
        assertEquals(listOf("Сборка 84 из коммита 9ef69cb: fix(home)."), WatchUpdater.parseNotes("\nСборка 84 из коммита 9ef69cb: fix(home).\n"))
        assertEquals(emptyList<String>(), WatchUpdater.parseNotes(""))
    }

    @Test
    fun `releases need a build tag and an apk, newest first`() {
        val json =
            """
            [
              {"tag_name": "v13.7.0-watch.3-b84", "draft": false, "prerelease": false, "body": "- Главная",
               "published_at": "2026-09-25T10:00:00Z",
               "assets": [{"name": "MetrolistWatch-13.7.0-watch.3-b84.apk", "browser_download_url": "https://x/84.apk", "size": 100}]},
              {"tag_name": "v13.7.0-watch.3-b85", "draft": false, "prerelease": false, "body": null,
               "published_at": "2026-09-26T10:00:00Z",
               "assets": [{"name": "notes.txt", "browser_download_url": "https://x/n", "size": 1},
                          {"name": "MetrolistWatch-13.7.0-watch.3-b85.apk", "browser_download_url": "https://x/85.apk", "size": 200}]},
              {"tag_name": "nightly", "draft": false, "prerelease": false, "body": "",
               "published_at": "2026-09-26T10:00:00Z",
               "assets": [{"name": "Metrolist.apk", "browser_download_url": "https://x/n.apk", "size": 1}]},
              {"tag_name": "v13.7.0-watch.3-b86", "draft": false, "prerelease": false, "body": "",
               "published_at": "2026-09-27T10:00:00Z", "assets": []}
            ]
            """.trimIndent()
        val releases = WatchUpdater.parseReleases(json)
        assertEquals(listOf(85, 84), releases.map { it.build })
        assertEquals("https://x/85.apk", releases[0].apkUrl)
        assertEquals(200L, releases[0].size)
        assertEquals(emptyList<String>(), releases[0].notes)
        assertEquals(listOf("Главная"), releases[1].notes)
    }
}
