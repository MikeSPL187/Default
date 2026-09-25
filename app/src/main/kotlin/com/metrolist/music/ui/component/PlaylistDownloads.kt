package com.metrolist.music.ui.component

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.playback.DownloadProgress
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent

/** How far a set of songs, such as a playlist, is from being on the device. */
@Immutable
data class PlaylistDownloads(
    val total: Int = 0,
    val done: Int = 0,
    /** Songs queued or downloading now. */
    val active: Int = 0,
    /** 0..1, counting the part of each running download already in. */
    val fraction: Float = 0f,
    val bytesPerSecond: Long = 0,
    val remainingBytes: Long = 0,
    val paused: Boolean = false,
) {
    val downloading: Boolean get() = active > 0
    val complete: Boolean get() = total > 0 && done == total
}

/** A typical song, for guessing what the songs still waiting will weigh. */
private const val TYPICAL_SONG_BYTES = 5L * 1024 * 1024

internal fun playlistDownloads(
    songIds: List<String>,
    downloads: Map<String, Download>,
    progress: DownloadProgress,
    paused: Boolean,
): PlaylistDownloads {
    if (songIds.isEmpty()) return PlaylistDownloads()
    var done = 0
    var active = 0
    var partial = 0f
    var knownBytes = 0L
    var knownCount = 0
    var waiting = 0
    var remaining = 0L
    songIds.forEach { id ->
        val download = downloads[id]
        when (download?.state) {
            Download.STATE_COMPLETED -> {
                done++
                if (download.contentLength > 0) {
                    knownBytes += download.contentLength
                    knownCount++
                }
            }
            Download.STATE_DOWNLOADING -> {
                active++
                partial += progress.fractions[id] ?: 0f
                val (got, length) = progress.bytes[id] ?: (0L to -1L)
                if (length > 0) remaining += (length - got).coerceAtLeast(0) else waiting++
            }
            Download.STATE_QUEUED -> {
                active++
                waiting++
            }
            else -> Unit
        }
    }
    val typical = if (knownCount > 0) knownBytes / knownCount else TYPICAL_SONG_BYTES
    return PlaylistDownloads(
        total = songIds.size,
        done = done,
        active = active,
        fraction = ((done + partial) / songIds.size).coerceIn(0f, 1f),
        bytesPerSecond = progress.bytesPerSecond,
        remainingBytes = remaining + waiting * typical,
        paused = paused,
    )
}

@Composable
fun rememberPlaylistDownloads(songIds: List<String>): PlaylistDownloads {
    val util = LocalDownloadUtil.current
    val downloads by util.downloads.collectAsStateWithLifecycle()
    val progress by util.progress.collectAsStateWithLifecycle()
    val paused by util.paused.collectAsStateWithLifecycle()
    return remember(songIds, downloads, progress, paused) { playlistDownloads(songIds, downloads, progress, paused) }
}

/**
 * The download button of a playlist: a ring around it fills as the songs come in, and it turns
 * into a check once all of them are on the device.
 */
@Composable
fun DownloadRingButton(
    state: PlaylistDownloads,
    onClick: () -> Unit,
    size: Dp = 48.dp,
) {
    val colors = MaterialTheme.colorScheme
    val shown by animateFloatAsState(state.fraction, label = "playlist download")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surfaceVariant,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (state.downloading) {
                CircularProgressIndicator(
                    progress = { shown },
                    strokeWidth = 3.dp,
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                painter =
                    painterResource(
                        when {
                            state.complete -> R.drawable.offline
                            state.downloading && state.paused -> R.drawable.pause
                            else -> R.drawable.download
                        },
                    ),
                contentDescription = stringResource(R.string.action_download),
                tint = if (state.complete || state.downloading) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A card under the playlist header while its songs download: how many, how fast, how long. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadProgressCard(
    state: PlaylistDownloads,
    onPauseToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.downloading,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val colors = MaterialTheme.colorScheme
        val shown by animateFloatAsState(state.fraction, label = "download card")
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (state.paused) R.string.download_paused else R.string.download_in_progress),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.download_count_of, state.done, state.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (!state.paused && state.bytesPerSecond > 0) {
                        Text(
                            stringResource(R.string.download_speed, Formatter.formatShortFileSize(context, state.bytesPerSecond)),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    LinearWavyProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onPauseToggle) {
                        Icon(
                            painterResource(if (state.paused) R.drawable.play else R.drawable.pause),
                            contentDescription = stringResource(if (state.paused) R.string.download_resume else R.string.download_pause),
                        )
                    }
                }
                Text(
                    text = etaText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun etaText(state: PlaylistDownloads): String {
    val keepsGoing = stringResource(R.string.download_keeps_going)
    if (state.paused || state.bytesPerSecond <= 0) return keepsGoing
    val minutes = (state.remainingBytes / state.bytesPerSecond / 60).toInt()
    val eta =
        if (minutes < 1) {
            stringResource(R.string.download_eta_soon)
        } else {
            pluralStringResource(R.plurals.download_eta_minutes, minutes, minutes)
        }
    return "$eta  ·  $keepsGoing"
}

/**
 * A soft glow in the colours of [artworkUrl] behind a header, reaching [extendUp] above it (under
 * the top bar) and fading into the page.
 */
@Composable
fun ArtworkGlow(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    extendUp: Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    val accent = rememberArtworkAccent(artworkUrl) ?: return
    val glow by animateColorAsState(accent, label = "artwork glow")
    val background = colors.background
    Canvas(
        modifier.layout { measurable, constraints ->
            val extra = extendUp.roundToPx()
            val placeable =
                measurable.measure(
                    constraints.copy(
                        minHeight = constraints.minHeight + extra,
                        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extra else constraints.maxHeight,
                    ),
                )
            layout(placeable.width, (placeable.height - extra).coerceAtLeast(0)) { placeable.place(0, -extra) }
        },
    ) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.45f), glow.copy(alpha = 0f)),
                center = Offset(w * 0.5f, h * 0.2f),
                radius = maxOf(w, h) * 0.75f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.25f), glow.copy(alpha = 0f)),
                center = Offset(w * 0.1f, h * 0.05f),
                radius = w * 0.6f,
            ),
        )
        drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to background))
    }
}

/** A thin line of a playlist's download progress, for under a collapsed top bar. */
@Composable
fun DownloadProgressLine(
    state: PlaylistDownloads,
    modifier: Modifier = Modifier,
) {
    if (!state.downloading) return
    val shown by animateFloatAsState(state.fraction, label = "download line")
    LinearProgressIndicator(
        progress = { shown },
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(2.dp),
    )
}

/** Which songs of a playlist to list, by whether they are on the device. */
enum class DownloadFilter { ALL, DOWNLOADED, MISSING }

/** Chips to show all songs, only downloaded ones or only missing ones; hidden when all are alike. */
@Composable
fun DownloadFilterChips(
    state: PlaylistDownloads,
    filter: DownloadFilter,
    onFilter: (DownloadFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.done == 0 || state.done == state.total) return
    ChipsRow(
        chips =
            listOf(
                DownloadFilter.ALL to stringResource(R.string.playlist_filter_all, state.total),
                DownloadFilter.DOWNLOADED to stringResource(R.string.playlist_filter_downloaded, state.done),
                DownloadFilter.MISSING to stringResource(R.string.playlist_filter_missing, state.total - state.done),
            ),
        currentValue = filter,
        onValueUpdate = onFilter,
        modifier = modifier,
    )
}

fun DownloadFilter.accepts(state: Int?): Boolean =
    when (this) {
        DownloadFilter.ALL -> true
        DownloadFilter.DOWNLOADED -> state == Download.STATE_COMPLETED
        DownloadFilter.MISSING -> state != Download.STATE_COMPLETED
    }
