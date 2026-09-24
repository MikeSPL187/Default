/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.db.entities.SongWithStats
import com.metrolist.music.ui.screens.wrapped.components.WrappedTopItemPage
import com.metrolist.music.ui.screens.wrapped.components.minutesText
import com.metrolist.music.utils.joinToArtistString

@Composable
fun WrappedTopSongScreen(topSong: SongWithStats?, isVisible: Boolean) {
    val andWord = stringResource(R.string.and)
    WrappedTopItemPage(
        heading = stringResource(R.string.wrapped_top_song_title),
        imageUrl = topSong?.thumbnailUrl,
        name = topSong?.title ?: stringResource(R.string.wrapped_no_data),
        subtitle = topSong?.let { song -> song.artists.joinToArtistString(" $andWord ") { it.name }.ifBlank { song.artistName.orEmpty() } },
        caption = minutesText(topSong?.timeListened),
        isVisible = isVisible,
    )
}

