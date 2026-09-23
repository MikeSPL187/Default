package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchExportTest {
    @Test
    fun `file names drop characters Android storage rejects`() {
        assertEquals("AC_DC - Back _ Black", sanitizeWatchExportName("AC/DC - Back / Black", "id"))
        assertEquals("Song", sanitizeWatchExportName("  Song.. ", "id"))
        assertEquals("id", sanitizeWatchExportName(" ... ", "id"))
        assertEquals(180, sanitizeWatchExportName("x".repeat(300), "id").length)
    }

    @Test
    fun `only aac in mp4 is watch compatible`() {
        assertTrue(isWatchCompatible("audio/mp4", "mp4a.40.2"))
        assertTrue(isWatchCompatible("audio/mp4", ""))
        assertFalse(isWatchCompatible("audio/webm", "opus"))
        assertFalse(isWatchCompatible("audio/mp4", "opus"))
    }

    @Test
    fun `error pages are not accepted as audio`() {
        assertFalse(isAudioContentType("text/html; charset=utf-8"))
        assertFalse(isAudioContentType("application/json"))
        assertTrue(isAudioContentType("audio/webm"))
        assertTrue(isAudioContentType("application/octet-stream"))
    }
}
