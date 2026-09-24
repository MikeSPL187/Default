/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.metrolist.music.R
import com.metrolist.music.ui.screens.wrapped.components.DecorativeCorners
import com.metrolist.music.ui.screens.wrapped.components.WrappedCounterPage

@Composable
fun WrappedTotalArtistsScreen(
    uniqueArtistCount: Int,
    isVisible: Boolean,
) {
    WrappedCounterPage(
        heading = AnnotatedString(stringResource(R.string.wrapped_total_artists_title)),
        count = uniqueArtistCount.toLong(),
        caption = AnnotatedString(stringResource(R.string.wrapped_total_artists_subtitle)),
        isVisible = isVisible,
        background = { DecorativeCorners(isVisible, corners = listOf(Alignment.TopStart to 5, Alignment.BottomEnd to 5), spread = 150) },
    )
}
