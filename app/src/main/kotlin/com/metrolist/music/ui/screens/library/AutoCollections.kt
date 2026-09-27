package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import androidx.compose.foundation.background
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
    val subtitle: String? = null,
)

/** Liked, downloaded, top, cached and uploaded songs: always a tap away, above everything else. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoCollectionsRow(
    collections: List<AutoCollection>,
    onOpen: (AutoCollection) -> Unit,
    modifier: Modifier = Modifier,
    onPlay: ((AutoCollection, shuffled: Boolean) -> Unit)? = null,
    playable: Set<String> = emptySet(),
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        items(collections, key = { it.key }) { collection ->
            // Holding a tile plays it right away, as is or shuffled, without opening it.
            var menu by remember { mutableStateOf(false) }
            Column(
                modifier =
                    Modifier
                        .width(TileSize)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onOpen(collection)
                            },
                            onLongClick =
                                if (onPlay != null && collection.key in playable) {
                                    { menu = true }
                                } else {
                                    null
                                },
                        ),
            ) {
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.collection_play)) },
                        leadingIcon = { Icon(painterResource(R.drawable.play), contentDescription = null) },
                        onClick = {
                            menu = false
                            onPlay?.invoke(collection, false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shuffle)) },
                        leadingIcon = { Icon(painterResource(R.drawable.shuffle), contentDescription = null) },
                        onClick = {
                            menu = false
                            onPlay?.invoke(collection, true)
                        },
                    )
                }
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
                collection.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

private val TileSize = 88.dp
