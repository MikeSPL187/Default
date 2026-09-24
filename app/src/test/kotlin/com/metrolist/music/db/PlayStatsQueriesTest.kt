package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.ArtistSongSortType
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.RelatedSongMap
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks the result order and contents of the play-statistics and related-song queries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayStatsQueriesTest {
    private lateinit var database: InternalDatabase
    private val dao get() = database.dao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        listOf("a", "b", "c", "d", "seed", "other").forEach { dao.insert(SongEntity(id = it, title = it)) }
        listOf("artist", "quiet", "fan").forEach { dao.insert(ArtistEntity(id = it, name = it)) }
        listOf("a", "b", "c").forEachIndexed { index, id -> dao.insert(SongArtistMap(songId = id, artistId = "artist", position = index)) }
        dao.insert(SongArtistMap(songId = "d", artistId = "quiet", position = 0))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private var eventId = 0L

    private fun play(songId: String, seconds: Long, at: LocalDateTime = NOW.minusDays(1)) {
        dao.insert(Event(id = ++eventId, songId = songId, timestamp = at, playTime = seconds * 1000))
    }

    @Test
    fun `most played songs come most played first`() = runBlocking {
        play("a", 10)
        play("b", 300)
        play("c", 60)
        play("c", 60)

        val songs = dao.mostPlayedSongs(fromTimeStamp = NOW.minusDays(7), limit = 10, toTimeStamp = NOW).first()

        assertEquals(listOf("b", "c", "a"), songs.map { it.id })
    }

    @Test
    fun `artist top song in a period is the most played one`() = runBlocking {
        play("a", 10)
        play("b", 300)
        play("c", 60)

        val songs =
            dao.artistSongs(
                artistId = "artist",
                sortType = ArtistSongSortType.PLAY_TIME,
                descending = true,
                fromTimeStamp = NOW.minusDays(7),
                toTimeStamp = NOW,
            ).first()

        assertEquals(listOf("b", "c", "a"), songs.map { it.id })
    }

    @Test
    fun `limit keeps the top songs, not the bottom ones`() = runBlocking {
        play("a", 10)
        play("b", 300)
        play("c", 60)

        val songs =
            dao.artistSongs(
                artistId = "artist",
                sortType = ArtistSongSortType.PLAY_TIME,
                descending = true,
                fromTimeStamp = NOW.minusDays(7),
                toTimeStamp = NOW,
                limit = 2,
            ).first()

        assertEquals(listOf("b", "c"), songs.map { it.id })
    }

    @Test
    fun `artists by play time put played artists first, then the rest`() = runBlocking {
        play("a", 10)
        play("d", 500)
        dao.insert(ArtistEntity(id = "fav", name = "fav", bookmarkedAt = NOW))

        val artists = dao.allArtistsByPlayTime().first()

        assertEquals(listOf("quiet", "artist"), artists.take(2).map { it.id })
        assertEquals(setOf("fan", "fav"), artists.drop(2).map { it.id }.toSet())
        assertEquals(1, artists.first { it.id == "artist" }.songCount)
        assertEquals(0, artists.first { it.id == "fav" }.songCount)
    }

    @Test
    fun `related songs only come from the given seed`() = runBlocking {
        dao.insert(RelatedSongMap(songId = "seed", relatedSongId = "a"))
        dao.insert(RelatedSongMap(songId = "other", relatedSongId = "a"))
        dao.insert(RelatedSongMap(songId = "other", relatedSongId = "b"))
        dao.insert(RelatedSongMap(songId = "seed", relatedSongId = "c"))

        val related = dao.getRelatedSongs("seed").first()

        assertEquals(setOf("a", "c"), related.map { it.id }.toSet())
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 20, 12, 0)
    }
}
