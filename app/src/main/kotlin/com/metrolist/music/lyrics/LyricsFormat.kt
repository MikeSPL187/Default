/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

// A real LRC timestamp at the start of a line: [mm:ss], [mm:ss.xx] or [mm:ss:xx].
private val LRC_TIMESTAMP_HINT = Regex("""(?m)^[ \t]*\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")

/**
 * Whether raw lyrics text appears to be time-synced (LRC-style), including when a BOM or
 * leading blank lines precede the first `[mm:ss.xx]` tag. Plain lyrics that merely start with
 * a bracketed note such as `[Verse 1]` are not treated as synced.
 */
fun lyricsTextLooksSynced(lyrics: String?): Boolean {
    if (lyrics.isNullOrBlank()) return false
    val t = lyrics.trim().removePrefix("\uFEFF").trimStart()
    return LRC_TIMESTAMP_HINT.containsMatchIn(t.take(4096))
}
