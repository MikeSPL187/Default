package com.metrolist.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent

/**
 * The head of anything played as a list, album or playlist alike: the cover lit by its own colour,
 * the title, who made it, a line of facts, and "Play" and "Shuffle" side by side under the thumb.
 */
@Composable
fun CollectionHeader(
    title: String,
    thumbnailUrl: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    playEnabled: Boolean = true,
    byline: (@Composable () -> Unit)? = null,
    cover: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val accent = rememberArtworkAccent(thumbnailUrl) ?: MaterialTheme.colorScheme.primary
    val glow by animateColorAsState(accent, tween(700), label = "collection glow")
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.18f),
                    radius = size.width * 0.85f,
                ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .size(CoverSize)
                    .shadow(24.dp, RoundedCornerShape(CoverRadius))
                    .clip(RoundedCornerShape(CoverRadius)),
            ) {
                if (cover != null) {
                    cover()
                } else {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            byline?.let {
                Box(Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp)) { it() }
            }
            meta?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                )
            }
            below?.invoke()
            CollectionPlayButtons(
                onPlay = onPlay,
                onShuffle = onShuffle,
                enabled = playEnabled,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

private val CoverSize = 212.dp
private val CoverRadius = 26.dp

/**
 * "Play" and "Shuffle" side by side, the width of the screen. While the collection itself plays,
 * the first one pauses and resumes it.
 */
@Composable
fun CollectionPlayButtons(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    playing: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
    ) {
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onPlay()
            },
            enabled = enabled,
            shape = CircleShape,
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) {
            Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (playing) R.string.player_pause else R.string.collection_play), style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onShuffle()
            },
            enabled = enabled,
            shape = CircleShape,
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) {
            Icon(painterResource(R.drawable.shuffle), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A small round secondary action under the play buttons: smart shuffle, download, menu. */
@Composable
fun CollectionSideAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.FilledTonalIconButton(onClick = onClick, modifier = modifier.size(44.dp)) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

/** A running time the way people say it: "1 h 38 min", or "42 min". */
@Composable
fun collectionDuration(seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 && minutes % 60 > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes % 60)
        hours > 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
    }
}
