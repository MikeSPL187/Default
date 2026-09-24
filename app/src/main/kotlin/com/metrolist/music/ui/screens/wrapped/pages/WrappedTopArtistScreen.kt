/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.ui.screens.wrapped.components.WrappedTopItemPage
import com.metrolist.music.ui.screens.wrapped.components.minutesText

@Composable
fun WrappedTopArtistScreen(title: String, topArtist: Artist?, isVisible: Boolean) {
    WrappedTopItemPage(
        heading = title,
        imageUrl = topArtist?.artist?.thumbnailUrl,
        name = topArtist?.artist?.name ?: stringResource(R.string.wrapped_no_data),
        subtitle = null,
        caption = minutesText(topArtist?.timeListened?.toLong()),
        isVisible = isVisible,
        circleImage = true,
    )
}
