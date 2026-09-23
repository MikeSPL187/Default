package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSyncPlanTest {
    @Test
    fun `exports songs not on the watch yet`() {
        val plan = planWatchSync(wantedIds = listOf("a", "b", "c"), exportedIds = setOf("b"), syncOwnedIds = emptySet())
        assertEquals(listOf("a", "c"), plan.toExport)
        assertEquals(emptySet<String>(), plan.toRemove)
    }

    @Test
    fun `removes only files the sync exported`() {
        val plan =
            planWatchSync(
                wantedIds = listOf("a"),
                exportedIds = setOf("a", "manual", "synced"),
                syncOwnedIds = setOf("a", "synced"),
            )
        // "manual" was exported by hand, so it stays although no synced playlist holds it.
        assertEquals(setOf("synced"), plan.toRemove)
        assertEquals(emptyList<String>(), plan.toExport)
    }

    @Test
    fun `turning sync off removes everything it exported`() {
        val plan = planWatchSync(wantedIds = emptyList(), exportedIds = setOf("x", "y"), syncOwnedIds = setOf("x"))
        assertEquals(setOf("x"), plan.toRemove)
    }

    @Test
    fun `songs in several synced playlists are exported once`() {
        val plan = planWatchSync(wantedIds = listOf("a", "a", "b"), exportedIds = emptySet(), syncOwnedIds = emptySet())
        assertEquals(listOf("a", "b"), plan.toExport)
    }
}
