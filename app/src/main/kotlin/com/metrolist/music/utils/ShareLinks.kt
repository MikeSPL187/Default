/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.constants.ShareYouTubeLinksKey

/**
 * Link used when sharing a song. Plain YouTube links open for everyone, including people
 * without YouTube Music, so they can be chosen in settings.
 */
fun Context.songShareUrl(videoId: String?): String =
    if (dataStore.get(ShareYouTubeLinksKey, false)) {
        "https://youtu.be/$videoId"
    } else {
        "https://music.youtube.com/watch?v=$videoId"
    }
