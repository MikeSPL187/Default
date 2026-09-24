/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.db.entities.SongWithStats
import com.metrolist.music.ui.screens.wrapped.components.RankedItem
import com.metrolist.music.ui.screens.wrapped.components.WrappedTopListPage
import com.metrolist.music.utils.joinToArtistString

@Composable
fun WrappedTop5SongsScreen(title: String, topSongs: List<SongWithStats>, isVisible: Boolean) {
    val andWord = stringResource(R.string.and)
    WrappedTopListPage(
        title = title,
        items =
            topSongs.take(5).map { song ->
                RankedItem(
                    imageUrl = song.thumbnailUrl,
                    title = song.title,
                    subtitle = song.artists.joinToArtistString(" $andWord ") { it.name }.ifBlank { song.artistName.orEmpty() },
                )
            },
        isVisible = isVisible,
    )
}
