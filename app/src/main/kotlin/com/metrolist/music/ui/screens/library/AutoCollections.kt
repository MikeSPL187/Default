package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A collection the app keeps by itself, shown as a coloured tile at the top of the library. */
@Immutable
data class AutoCollection(
    val key: String,
    val title: String,
    val icon: Int,
    val background: Brush,
    val tint: Color,
    val route: String,
)

/** Liked, downloaded, top, cached and uploaded songs: always a tap away, above everything else. */
@Composable
fun AutoCollectionsRow(
    collections: List<AutoCollection>,
    onOpen: (AutoCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        items(collections, key = { it.key }) { collection ->
            Column(
                modifier =
                    Modifier
                        .width(TileSize)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onOpen(collection)
                        },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(TileSize)
                            .clip(RoundedCornerShape(20.dp))
                            .background(collection.background),
                ) {
                    Icon(painterResource(collection.icon), contentDescription = null, tint = collection.tint, modifier = Modifier.size(34.dp))
                }
                Text(
                    collection.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                )
            }
        }
    }
}

private val TileSize = 88.dp
