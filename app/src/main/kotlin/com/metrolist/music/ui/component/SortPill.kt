package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

/**
 * The sort of a song list as a pill: the current order with a menu of the others, and beside it a
 * round button flipping the direction (not offered for a hand-made order, which has none).
 */
@Composable
fun <T : Enum<T>> SortPill(
    options: List<T>,
    sortType: T,
    sortDescending: Boolean,
    onSortTypeChange: (T) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    label: (T) -> Int,
    modifier: Modifier = Modifier,
    hasDirection: (T) -> Boolean = { true },
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(vertical = 8.dp)) {
        Box {
            Surface(
                onClick = { menuOpen = true },
                shape = CircleShape,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.sort), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(label(sortType)),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                    )
                    Icon(painterResource(R.drawable.expand_more), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.widthIn(min = 196.dp),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label(option))) },
                        trailingIcon = {
                            if (option == sortType) Icon(painterResource(R.drawable.check), contentDescription = null, tint = colors.primary)
                        },
                        onClick = {
                            onSortTypeChange(option)
                            menuOpen = false
                        },
                    )
                }
            }
        }
        if (hasDirection(sortType)) {
            val rotation by animateFloatAsState(if (sortDescending) 0f else 180f, label = "sort direction")
            Surface(
                onClick = { onSortDescendingChange(!sortDescending) },
                shape = CircleShape,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                modifier = Modifier.padding(start = 8.dp).size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.arrow_downward),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).rotate(rotation),
                    )
                }
            }
        }
    }
}

/** Play or pause, crossfading as it switches. */
@Composable
fun PlayPauseIcon(
    playing: Boolean,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    androidx.compose.animation.Crossfade(targetState = playing, label = "play pause") { showPause ->
        Icon(
            painter = painterResource(if (showPause) R.drawable.pause else R.drawable.play),
            contentDescription = stringResource(if (showPause) R.string.pause else R.string.play),
            modifier = Modifier.size(size),
        )
    }
}

/**
 * Whether the player is on this playlist: its queue carries the playlist's name, a song of the
 * playlist is loaded, and it has not played out. The name alone outlives a closed player.
 */
fun isPlaylistQueued(
    queueTitle: String?,
    playlistName: String?,
    currentSongId: String?,
    playbackState: Int,
    inPlaylist: (String) -> Boolean,
): Boolean =
    queueTitle != null &&
        queueTitle == playlistName &&
        currentSongId != null &&
        playbackState != androidx.media3.common.Player.STATE_ENDED &&
        inPlaylist(currentSongId)
