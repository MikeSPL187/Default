package com.metrolist.music.playback

import androidx.media3.exoplayer.offline.Download
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartDownloadPlanTest {
    @Test
    fun `downloads missing songs and claims only new ones`() {
        val plan =
            planSmartDownloads(
                wantedIds = listOf("new", "done", "userQueued"),
                ownedIds = emptySet(),
                downloadStates = mapOf("done" to Download.STATE_COMPLETED, "userQueued" to Download.STATE_QUEUED),
                isLiked = { false },
            )
        assertEquals(listOf("new", "userQueued"), plan.toDownload)
        // A download the user started themselves is never taken over, so it is never removed later.
        assertEquals(setOf("new"), plan.toClaim)
    }

    @Test
    fun `songs leaving the set are released and removed unless liked`() {
        val plan =
            planSmartDownloads(
                wantedIds = listOf("stay"),
                ownedIds = setOf("stay", "gone", "likedGone"),
                downloadStates = mapOf("stay" to Download.STATE_COMPLETED),
                isLiked = { it == "likedGone" },
            )
        assertEquals(setOf("gone", "likedGone"), plan.release)
        assertEquals(setOf("gone"), plan.toRemove)
        assertEquals(emptyList<String>(), plan.toDownload)
    }

    @Test
    fun `disabling releases everything it owns`() {
        val plan =
            planSmartDownloads(
                wantedIds = emptyList(),
                ownedIds = setOf("a", "b"),
                downloadStates = emptyMap(),
                isLiked = { false },
            )
        assertEquals(setOf("a", "b"), plan.toRemove)
    }

    @Test
    fun `an owned song whose download vanished is downloaded and kept owned`() {
        val plan =
            planSmartDownloads(
                wantedIds = listOf("a"),
                ownedIds = setOf("a"),
                downloadStates = emptyMap(),
                isLiked = { false },
            )
        assertEquals(listOf("a"), plan.toDownload)
        assertEquals(setOf("a"), plan.toClaim)
    }
}
