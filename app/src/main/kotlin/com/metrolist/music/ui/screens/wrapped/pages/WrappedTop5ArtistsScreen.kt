/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.ui.screens.wrapped.components.AnimatedBackground
import com.metrolist.music.ui.screens.wrapped.components.RankedItem
import com.metrolist.music.ui.screens.wrapped.components.ShapeType
import com.metrolist.music.ui.screens.wrapped.components.WrappedTopListPage
import com.metrolist.music.ui.screens.wrapped.components.minutesText

@Composable
fun WrappedTop5ArtistsScreen(title: String, topArtists: List<Artist>, isVisible: Boolean) {
    WrappedTopListPage(
        title = title,
        items = topArtists.take(5).map { RankedItem(it.artist.thumbnailUrl, it.artist.name, minutesText(it.timeListened?.toLong())) },
        isVisible = isVisible,
        circleImages = true,
        background = { AnimatedBackground(elementCount = 15, shapeTypes = listOf(ShapeType.Line)) },
    )
}
