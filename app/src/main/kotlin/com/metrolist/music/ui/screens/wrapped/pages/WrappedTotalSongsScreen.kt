/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.metrolist.music.R
import com.metrolist.music.ui.screens.wrapped.components.AnimatedBackground
import com.metrolist.music.ui.screens.wrapped.components.ShapeType
import com.metrolist.music.ui.screens.wrapped.components.WrappedCounterPage

@Composable
fun WrappedTotalSongsScreen(
    uniqueSongCount: Int,
    isVisible: Boolean,
) {
    WrappedCounterPage(
        heading = AnnotatedString(stringResource(R.string.wrapped_total_songs_title)),
        count = uniqueSongCount.toLong(),
        caption = AnnotatedString(stringResource(R.string.wrapped_total_songs_subtitle)),
        isVisible = isVisible,
        background = { AnimatedBackground(shapeTypes = listOf(ShapeType.Line)) },
    )
}
