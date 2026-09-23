package com.metrolist.music.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFormatTest {
    @Test
    fun `lrc timestamps are synced`() {
        assertTrue(lyricsTextLooksSynced("[00:12.34]Hello\n[00:15.00]World"))
        assertTrue(lyricsTextLooksSynced("﻿\n[01:02]Line without fraction"))
        assertTrue(lyricsTextLooksSynced("[ar:Someone]\n[00:01:50]Colon fraction"))
    }

    @Test
    fun `bracketed notes are not synced`() {
        assertFalse(lyricsTextLooksSynced("[Verse 1]\nJust words"))
        assertFalse(lyricsTextLooksSynced("[Chorus]\nMore words [x2]"))
        assertFalse(lyricsTextLooksSynced(null))
    }
}
