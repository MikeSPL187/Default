/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.metrolist.music.R
import com.metrolist.music.db.entities.AlbumPlayStats
import com.metrolist.music.ui.screens.wrapped.components.AnimatedBackground
import com.metrolist.music.ui.screens.wrapped.components.RankedItem
import com.metrolist.music.ui.screens.wrapped.components.ShapeType
import com.metrolist.music.ui.screens.wrapped.components.WrappedCounterPage
import com.metrolist.music.ui.screens.wrapped.components.WrappedTopItemPage
import com.metrolist.music.ui.screens.wrapped.components.WrappedTopListPage
import com.metrolist.music.ui.screens.wrapped.components.minutesText

@Composable
fun WrappedTotalAlbumsScreen(uniqueAlbumCount: Int, isVisible: Boolean) {
    WrappedCounterPage(
        heading = AnnotatedString(stringResource(R.string.wrapped_total_albums_title)),
        count = uniqueAlbumCount.toLong(),
        caption = AnnotatedString(stringResource(R.string.wrapped_total_albums_subtitle)),
        isVisible = isVisible,
        background = { AnimatedBackground(shapeTypes = listOf(ShapeType.Circle)) },
    )
}

@Composable
fun WrappedTopAlbumScreen(topAlbum: AlbumPlayStats?, isVisible: Boolean) {
    WrappedTopItemPage(
        heading = stringResource(R.string.wrapped_top_album_title),
        imageUrl = topAlbum?.thumbnailUrl,
        name = topAlbum?.title ?: stringResource(R.string.wrapped_no_data),
        subtitle = null,
        caption = minutesText(topAlbum?.timeListened),
        isVisible = isVisible,
        background = { AnimatedBackground(shapeTypes = listOf(ShapeType.Rect)) },
    )
}

@Composable
fun WrappedTop5AlbumsScreen(title: String, topAlbums: List<AlbumPlayStats>, isVisible: Boolean) {
    WrappedTopListPage(
        title = title,
        items = topAlbums.take(5).map { RankedItem(it.thumbnailUrl, it.title, minutesText(it.timeListened)) },
        isVisible = isVisible,
        background = { AnimatedBackground(shapeTypes = listOf(ShapeType.Circle)) },
    )
}
