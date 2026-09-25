package com.metrolist.music.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.PlayPauseIcon
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent
import com.metrolist.music.utils.RecentCollection
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.sin

/** Blocks of the home screen, in order; their ids are what "Edit home" stores. */
private enum class HomeBlock(val id: String, val label: Int) {
    DJ("dj", R.string.home_block_dj),
    QUICK_ACCESS("quick_access", R.string.home_block_quick_access),
    FOR_YOU("for_you", R.string.home_block_for_you),
    NEW_RELEASES("new_releases", R.string.home_block_new_releases),
    CHART("chart", R.string.home_block_chart),
    MOODS("moods", R.string.home_block_moods),
}

/**
 * The home screen: the DJ first, then quick access to what the user opens most, mixes made for
 * them, new releases, the chart and moods. Every block can be hidden from "Edit home".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenHomeBlocks.collectAsStateWithLifecycle()
    val recents by viewModel.recentCollections.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val daylist by viewModel.daylist.collectAsStateWithLifecycle()
    val dailyDiscover by viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val forgotten by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val newReleases by viewModel.newReleases.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    var editing by remember { mutableStateOf(false) }
    val shown = HomeBlock.entries.filter { it.id !in hidden }

    val likedTitle = stringResource(R.string.liked)
    val quickItems =
        remember(recents, keepListening, likedTitle) { quickAccessItems(recents, keepListening.orEmpty(), likedTitle) }
    val mixes =
        buildList {
            daylist?.let { list ->
                add(Mix(stringResource(list.part.titleRes), list.songs.map { it.toMediaItem() }, list.songs.map { it.song.thumbnailUrl }))
            }
            dailyDiscover?.mapNotNull { it.recommendation as? SongItem }?.takeIf { it.size >= MIN_MIX }?.let { songs ->
                add(Mix(stringResource(R.string.home_mix_discover), songs.map { it.toMediaItem() }, songs.map { it.thumbnail }))
            }
            forgotten?.takeIf { it.size >= MIN_MIX }?.let { songs ->
                add(Mix(stringResource(R.string.home_mix_forgotten), songs.map { it.toMediaItem() }, songs.map { it.song.thumbnailUrl }))
            }
        }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            viewModel.refresh()
        },
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            shown.forEach { block ->
                when (block) {
                    HomeBlock.DJ ->
                        item(key = "dj", contentType = "dj") {
                            DjCard(
                                active = queueTitle == stringResource(R.string.dj_title) && mediaMetadata != null,
                                isPlaying = isPlaying,
                                current = mediaMetadata,
                                next = playerConnection.nextMetadata(),
                                onStart = { title -> playerConnection.playQueue(viewModel.djQueue(title)) },
                                onToggle = playerConnection::togglePlayPause,
                                position = { playerConnection.player.currentPosition to playerConnection.player.duration },
                                modifier = Modifier.animateItem(),
                            )
                        }

                    HomeBlock.QUICK_ACCESS ->
                        if (quickItems.isNotEmpty()) {
                            item(key = "quick_access", contentType = "quick_access") {
                                QuickAccessGrid(
                                    items = quickItems,
                                    playingTitle = queueTitle.takeIf { isPlaying },
                                    onOpen = { navController.navigate(it.route) },
                                    onForget = { it.recent?.let(viewModel::forgetRecent) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                    HomeBlock.FOR_YOU ->
                        if (mixes.isNotEmpty()) {
                            item(key = "for_you_title") { NavigationTitle(title = stringResource(R.string.home_block_for_you), modifier = Modifier.animateItem()) }
                            item(key = "for_you", contentType = "for_you") {
                                SnappingRow(mixes, key = { it.title }, itemWidth = 220.dp) { mix ->
                                    MixCard(
                                        mix = mix,
                                        playing = queueTitle == mix.title && isPlaying,
                                        onPlay = {
                                            if (queueTitle == mix.title) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(ListQueue(title = mix.title, items = mix.items))
                                            }
                                        },
                                    )
                                }
                            }
                        }

                    HomeBlock.NEW_RELEASES ->
                        newReleases?.takeIf { it.isNotEmpty() }?.let { albums ->
                            item(key = "releases_title") {
                                NavigationTitle(
                                    title = stringResource(R.string.home_block_new_releases),
                                    label = stringResource(R.string.home_releases_label),
                                    onClick = { navController.navigate("new_release") },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "releases", contentType = "releases") {
                                SnappingRow(albums.take(RELEASES), key = { it.id }, itemWidth = 144.dp) { album ->
                                    ReleaseCard(album = album, onOpen = { navController.navigate("album/${album.browseId}") })
                                }
                            }
                        }

                    HomeBlock.CHART ->
                        chart?.takeIf { it.isNotEmpty() }?.let { songs ->
                            item(key = "chart_title") {
                                NavigationTitle(
                                    title = stringResource(R.string.home_block_chart),
                                    onClick = { navController.navigate("charts_screen") },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "chart", contentType = "chart") {
                                ChartList(
                                    songs = songs,
                                    currentId = mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    onPlay = { index ->
                                        playerConnection.playQueue(
                                            ListQueue(title = null, items = songs.map { it.toMediaItem() }, startIndex = index),
                                        )
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                    HomeBlock.MOODS ->
                        explorePage?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
                            item(key = "moods_title") {
                                NavigationTitle(
                                    title = stringResource(R.string.home_block_moods),
                                    onClick = { navController.navigate("mood_and_genres") },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "moods", contentType = "moods") {
                                MoodGrid(
                                    moods = moods.distinctBy { it.title }.take(MOODS),
                                    onOpen = { navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}") },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                }
            }

            item(key = "edit_home") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .animateItem(),
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            editing = true
                        },
                    ) {
                        Icon(painterResource(R.drawable.edit), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_edit))
                    }
                }
            }
        }
    }

    if (editing) {
        ModalBottomSheet(onDismissRequest = { editing = false }) {
            Text(
                stringResource(R.string.home_edit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
            HomeBlock.entries.forEach { block ->
                val visible = block.id !in hidden
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(block.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = visible,
                        onCheckedChange = { show ->
                            haptic.performHapticFeedback(if (show) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            viewModel.setHomeBlockHidden(block.id, !show)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The song after the current one in the queue, for the DJ card. */
@Composable
private fun com.metrolist.music.playback.PlayerConnection.nextMetadata(): MediaMetadata? {
    val windows by queueWindows.collectAsStateWithLifecycle()
    val index by currentWindowIndex.collectAsStateWithLifecycle()
    return windows.getOrNull(index + 1)?.mediaItem?.metadata
}

private const val MIN_MIX = 5
private const val RELEASES = 10
private const val MOODS = 6

// ---------------------------------------------------------------- shared bits

/** A card that sinks a little under the finger, on a soft spring. */
@Composable
private fun rememberPress(): Pair<MutableInteractionSource, Float> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return source to scale
}

/** A row that settles on whole cards, ticking softly as each one lands. */
@Composable
private fun <T> SnappingRow(
    items: List<T>,
    key: (T) -> Any,
    itemWidth: Dp,
    content: @Composable (T) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val state = rememberLazyListState()
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            if (index > 0 || state.firstVisibleItemScrollOffset > 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        items(items, key = key) { item -> Box(Modifier.width(itemWidth)) { content(item) } }
    }
}

// ---------------------------------------------------------------- DJ

/**
 * The DJ card: the theme's colour lit by the artwork of what plays, a live waveform, and one
 * button. Idle it says what the DJ does; playing it shows the song and the next one.
 */
@Composable
private fun DjCard(
    active: Boolean,
    isPlaying: Boolean,
    current: MediaMetadata?,
    next: MediaMetadata?,
    onStart: (String) -> Unit,
    onToggle: () -> Unit,
    position: () -> Pair<Long, Long>,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val title = stringResource(R.string.dj_title)
    var starting by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) starting = false }
    LaunchedEffect(starting) {
        // The DJ looks songs up first; a failure must not leave the button spinning.
        if (starting) {
            delay(START_TIMEOUT_MS)
            starting = false
        }
    }
    val accent = rememberArtworkAccent(if (active) current?.thumbnailUrl else null) ?: colors.tertiary
    val glow by animateColorAsState(accent, tween(900), label = "dj glow")
    val (source, scale) = rememberPress()

    Box(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(28.dp))
                .background(lerp(colors.primaryContainer, colors.surface, 0.25f))
                .combinedClickable(interactionSource = source, indication = null, onClick = {
                    if (active) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onToggle()
                    } else if (!starting) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        starting = true
                        onStart(title)
                    }
                }),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.1f, 0f), w * 0.8f))
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), Color.Transparent), Offset(w * 0.95f, h * 0.2f), w * 0.75f))
            drawRect(Brush.radialGradient(listOf(colors.tertiary.copy(alpha = 0.45f), Color.Transparent), Offset(w * 0.5f, h * 1.1f), w * 0.7f))
        }
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Waveform(
                playing = active && isPlaying,
                progress = if (active) position else null,
                modifier =
                    Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                        .height(40.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = if (active) current else null,
                    transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                    contentKey = { it?.id },
                    label = "dj now",
                    modifier = Modifier.weight(1f),
                ) { song ->
                    if (song == null) {
                        Text(
                            stringResource(R.string.dj_idle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                )
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.artists.joinToString { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            next?.let {
                                Text(
                                    stringResource(R.string.dj_next, it.title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }
                Surface(shape = CircleShape, color = Color.White, contentColor = lerp(colors.primary, Color.Black, 0.55f), modifier = Modifier.padding(start = 12.dp).size(60.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (starting && !active) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp), color = lerp(colors.primary, Color.Black, 0.4f))
                        } else {
                            PlayPauseIcon(playing = active && isPlaying, size = 28.dp)
                        }
                    }
                }
            }
        }
    }
}

private const val START_TIMEOUT_MS = 15_000L

/** Bars that sway while music plays; the part of the song already heard is drawn brighter. */
@Composable
private fun Waveform(
    playing: Boolean,
    progress: (() -> Pair<Long, Long>)?,
    modifier: Modifier = Modifier,
) {
    val phase by produceState(0f, playing) {
        if (!playing) return@produceState
        val start = value
        val origin = withFrameMillis { it }
        while (true) withFrameMillis { value = start + (it - origin) * WAVE_SPEED }
    }
    val heard by produceState(0f, playing, progress) {
        while (true) {
            value = progress?.invoke()?.let { (at, total) -> if (total > 0) (at.toFloat() / total).coerceIn(0f, 1f) else 0f } ?: 0f
            if (!playing) break
            delay(PROGRESS_FRAME_MS)
        }
    }
    val lift by animateFloatAsState(if (playing) 1f else 0.55f, tween(600), label = "wave lift")
    Canvas(modifier) {
        val bars = WAVE_BARS
        val gap = size.width / bars
        val barWidth = gap * 0.45f
        for (i in 0 until bars) {
            val base = 0.35f + 0.65f * abs(sin(i * 0.45f)) * (0.6f + 0.4f * sin(i * 0.17f))
            val sway = if (playing) 0.25f * sin(phase + i * 0.5f) else 0f
            val h = size.height * ((base + sway).coerceIn(0.15f, 1f)) * lift
            val x = i * gap + (gap - barWidth) / 2
            val lit = progress == null || i.toFloat() / bars <= heard
            drawRoundRect(
                color = Color.White.copy(alpha = if (lit) 0.9f else 0.35f),
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private const val WAVE_BARS = 44
private const val WAVE_SPEED = 0.0024f
private const val PROGRESS_FRAME_MS = 500L

// ---------------------------------------------------------------- quick access

@Immutable
private data class QuickItem(
    val title: String,
    val subtitle: Int,
    val thumbnail: String?,
    val route: String,
    val round: Boolean = false,
    val recent: RecentCollection? = null,
)

/** Six ways back to what the user opens most: the last collections, then their liked songs and favourites. */
private fun quickAccessItems(
    recents: List<RecentCollection>,
    keepListening: List<LocalItem>,
    likedTitle: String,
): List<QuickItem> {
    val fromRecents =
        recents.map { recent ->
            QuickItem(
                title = recent.title,
                subtitle =
                    when (recent.kind) {
                        RecentCollection.Kind.PLAYLIST, RecentCollection.Kind.AUTO_PLAYLIST -> R.string.home_kind_playlist
                        RecentCollection.Kind.ALBUM -> R.string.home_kind_album
                        RecentCollection.Kind.ARTIST -> R.string.home_kind_artist
                    },
                thumbnail = recent.thumbnail,
                route = recent.route,
                round = recent.kind == RecentCollection.Kind.ARTIST,
                recent = recent,
            )
        }
    val liked = QuickItem(likedTitle, R.string.home_kind_playlist, null, "auto_playlist/liked")
    val favourites =
        keepListening.mapNotNull { item ->
            when (item) {
                is Album -> QuickItem(item.title, R.string.home_kind_album, item.thumbnailUrl, "album/${item.id}")
                is Artist -> QuickItem(item.title, R.string.home_kind_artist, item.thumbnailUrl, "artist/${item.id}", round = true)
                else -> null
            }
        }
    return (fromRecents + liked + favourites).distinctBy { it.route }.take(QUICK_ACCESS)
}

private const val QUICK_ACCESS = 6

@Composable
private fun QuickAccessGrid(
    items: List<QuickItem>,
    playingTitle: String?,
    onOpen: (QuickItem) -> Unit,
    onForget: (QuickItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    QuickTile(item, playing = item.title == playingTitle, onOpen = { onOpen(item) }, onForget = { onForget(item) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(
    item: QuickItem,
    playing: Boolean,
    onOpen: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val (source, scale) = rememberPress()
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainer)
                    .combinedClickable(
                        interactionSource = source,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onOpen()
                        },
                        onLongClick = {
                            if (item.recent != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            }
                        },
                    ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(colors.surfaceContainerHighest),
            ) {
                if (item.thumbnail != null) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(if (item.round) Modifier.padding(6.dp).clip(CircleShape) else Modifier),
                    )
                } else {
                    Icon(painterResource(R.drawable.favorite), contentDescription = null, tint = colors.primary)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(item.subtitle), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
            if (playing) EqualizerBars(Modifier.padding(end = 10.dp).size(14.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_forget)) },
                leadingIcon = { Icon(painterResource(R.drawable.close), contentDescription = null) },
                onClick = {
                    menu = false
                    onForget()
                },
            )
        }
    }
}

/** Three small bars that bounce while something plays. */
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val phase by produceState(0f) {
        val origin = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - origin) * WAVE_SPEED * 2 }
    }
    Canvas(modifier) {
        val w = size.width / 5
        for (i in 0..2) {
            val h = size.height * (0.35f + 0.65f * abs(sin(phase + i * 1.3f)))
            drawRoundRect(color, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

// ---------------------------------------------------------------- for you

@Immutable
private data class Mix(
    val title: String,
    val items: List<androidx.media3.common.MediaItem>,
    val covers: List<String?>,
)

@Composable
private fun MixCard(
    mix: Mix,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val accent = rememberArtworkAccent(mix.covers.firstOrNull { it != null }) ?: colors.primary
    val tint by animateColorAsState(accent, tween(700), label = "mix tint")
    val (source, scale) = rememberPress()
    Box(
        Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(lerp(tint, colors.surface, 0.55f))
            .combinedClickable(interactionSource = source, indication = null, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onPlay()
            }),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(listOf(tint.copy(alpha = 0.75f), Color.Transparent), Offset(size.width, 0f), size.width))
        }
        Row(Modifier.padding(16.dp)) {
            mix.covers.filterNotNull().distinct().take(3).forEachIndexed { index, cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .offset(x = (-12 * index).dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surface),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp, end = 64.dp),
        ) {
            Text(mix.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.n_song, mix.items.size, mix.items.size),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Surface(
            shape = CircleShape,
            color = Color.White,
            contentColor = lerp(tint, Color.Black, 0.6f),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { PlayPauseIcon(playing = playing, size = 20.dp) }
        }
    }
}

// ---------------------------------------------------------------- releases

@Composable
private fun ReleaseCard(
    album: AlbumItem,
    onOpen: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val (source, scale) = rememberPress()
    Column(
        Modifier
            .scale(scale)
            .combinedClickable(interactionSource = source, indication = null, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpen()
            }),
    ) {
        AsyncImage(
            model = album.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp)),
        )
        Text(album.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(
            album.artists.orEmpty().joinToString { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- chart

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartList(
    songs: List<SongItem>,
    currentId: String?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val colors = MaterialTheme.colorScheme
    Column(modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))) {
        songs.forEachIndexed { index, song ->
            val active = song.id == currentId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onPlay(index)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show { YouTubeSongMenu(song = song, onDismiss = menuState::dismiss) }
                            },
                        ).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) colors.primary else colors.onSurface,
                    modifier = Modifier.width(28.dp),
                )
                Box {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                    )
                    if (active && isPlaying) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                        ) { EqualizerBars(Modifier.size(16.dp)) }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (active) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- moods

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodGrid(
    moods: List<MoodAndGenres.Item>,
    onOpen: (MoodAndGenres.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp),
    ) {
        moods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mood ->
                    val tone = Color(mood.stripeColor.toInt()).copy(alpha = 1f)
                    val (source, scale) = rememberPress()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .scale(scale)
                                .clip(RoundedCornerShape(18.dp))
                                .background(lerp(tone, colors.surface, 0.82f))
                                .combinedClickable(interactionSource = source, indication = null, onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onOpen(mood)
                                }).padding(horizontal = 12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(lerp(tone, colors.surface, 0.55f)),
                        ) {
                            Icon(painterResource(moodIcon(mood.title)), contentDescription = null, tint = lerp(tone, Color.White, 0.35f), modifier = Modifier.size(18.dp))
                        }
                        Text(
                            mood.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** An icon that fits a mood's name, in English or Russian; a note for anything else. */
private fun moodIcon(title: String): Int {
    val name = title.lowercase()
    fun has(vararg words: String) = words.any { it in name }
    return when {
        has("chill", "relax", "calm", "спок", "расслаб", "чил") -> R.drawable.graphic_eq
        has("energy", "energ", "энерг", "бодр") -> R.drawable.bolt
        has("workout", "gym", "fitness", "трениров", "спорт") -> R.drawable.fitness_center
        has("focus", "study", "work", "фокус", "концентр", "учёб", "учеб") -> R.drawable.center_focus
        has("party", "вечерин", "туса") -> R.drawable.celebration
        has("sad", "груст", "печал") -> R.drawable.water_drop
        has("sleep", "сон", "сна") -> R.drawable.bedtime
        has("romance", "love", "романт", "любов") -> R.drawable.favorite
        else -> R.drawable.music_note
    }
}
