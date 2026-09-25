package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HiddenPlaylistsRestoreTest {
    private lateinit var database: InternalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun playlist(
        id: String,
        browseId: String? = null,
        withSong: Boolean = true,
    ) {
        val dao = database.dao
        dao.insert(PlaylistEntity(id = id, name = id, browseId = browseId, createdAt = LocalDateTime.of(2026, 9, 1, 12, 0)))
        if (withSong) dao.insert(PlaylistSongMap(playlistId = id, songId = "song"))
    }

    @Test
    fun `imported playlists come back to the library, others stay as they are`() =
        runBlocking {
            database.dao.insert(SongEntity(id = "song", title = "Song"))
            playlist("imported")
            playlist("youtube", browseId = "PL1")
            playlist("empty", withSong = false)

            assertEquals(1, database.dao.showHiddenLocalPlaylists())

            val restored = database.dao.playlist("imported").first()!!.playlist
            assertNotNull(restored.bookmarkedAt)
            assertEquals(LocalDateTime.of(2026, 9, 1, 12, 0), restored.bookmarkedAt)
            assertNull(database.dao.playlist("youtube").first()!!.playlist.bookmarkedAt)
            assertNull(database.dao.playlist("empty").first()!!.playlist.bookmarkedAt)
        }
}
