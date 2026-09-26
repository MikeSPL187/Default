package com.metrolist.music.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.AlbumProgress
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SectionHeader
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent
import com.metrolist.music.utils.DayPart
import com.metrolist.music.utils.RecentCollection
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlin.math.sin

/** Blocks of the home screen in their default order; their ids are what "Edit home" stores. */
private enum class HomeBlock(
    val id: String,
    val label: Int,
    val icon: Int,
) {
    DJ("dj", R.string.home_block_dj, R.drawable.graphic_eq),
    QUICK_ACCESS("quick_access", R.string.home_block_quick_access, R.drawable.grid_view),
    CONTINUE("continue", R.string.home_block_continue, R.drawable.history),
    FOR_YOU("for_you", R.string.home_block_for_you, R.drawable.auto_awesome),
    QUICK_PICKS("quick_picks", R.string.quick_picks, R.drawable.bolt),
    ARTISTS("artists", R.string.home_block_artists, R.drawable.person),
    NEW_RELEASES("new_releases", R.string.home_block_new_releases, R.drawable.new_releases),
    CHART("chart", R.string.home_block_chart, R.drawable.trending_up),
}

private fun orderedBlocks(order: List<String>): List<HomeBlock> =
    order.mapNotNull { id -> HomeBlock.entries.firstOrNull { it.id == id } } + HomeBlock.entries.filter { it.id !in order }

/**
 * Home: mood chips, the DJ's sphere, quick access to what the user opens most, albums to take up
 * again, mixes made for them, quick picks, their artists, new releases and the chart. A mood
 * turns the feed to what YouTube Music has for it and sends the DJ there too. Every block can be
 * hidden or moved from "Edit home".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenHomeBlocks.collectAsStateWithLifecycle()
    val order by viewModel.homeBlockOrder.collectAsStateWithLifecycle()
    val recents by viewModel.recentCollections.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val daylist by viewModel.daylist.collectAsStateWithLifecycle()
    val discoverMix by viewModel.discoverMix.collectAsStateWithLifecycle()
    val onRepeat by viewModel.onRepeat.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val forgotten by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val newReleases by viewModel.newReleases.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()
    val inProgress by viewModel.albumsInProgress.collectAsStateWithLifecycle()
    val artists by viewModel.yourArtists.collectAsStateWithLifecycle()
    val djMode by viewModel.djMode.collectAsStateWithLifecycle()
    val mood by viewModel.djMood.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    var editing by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }
    val blocks = remember(order) { orderedBlocks(order) }
    val shown = blocks.filter { it.id !in hidden }
    val moods =
        homePage?.chips.orEmpty().filter { chip ->
            chip.endpoint?.params != null && !chip.title.contains("podcast", ignoreCase = true) && !chip.title.contains("подкаст", ignoreCase = true)
        }

    // ---- DJ
    val djTitle = stringResource(R.string.dj_title)
    val djActive = queueTitle == djTitle && mediaMetadata != null
    var djStarting by remember { mutableStateOf(false) }
    LaunchedEffect(djActive) { if (djActive) djStarting = false }
    LaunchedEffect(djStarting) {
        // The DJ looks songs up first; a failure must not leave the button spinning.
        if (djStarting) {
            delay(START_TIMEOUT_MS)
            djStarting = false
        }
    }
    val retune = { if (djActive) playerConnection.service.retuneDj() }
    val pickMood: (HomePage.Chip?) -> Unit = { chip ->
        viewModel.selectMood(chip)
        retune()
    }
    val colors = MaterialTheme.colorScheme
    val artAccent = rememberArtworkAccent(if (djActive) mediaMetadata?.thumbnailUrl else null)
    val accent = artAccent ?: colors.primary
    val palette = remember(accent, colors.tertiary) { spherePalette(accent, colors.tertiary) }
    val previews =
        remember(onRepeat, quickPicks) {
            (onRepeat.orEmpty() + quickPicks.orEmpty()).mapNotNull { it.song.thumbnailUrl }.distinct().take(PREVIEWS)
        }
    val dayPart = remember { DayPart.now() }
    val discovery = remember(mediaMetadata?.id, djActive) { mediaMetadata?.id?.takeIf { djActive }?.let(playerConnection.service::isDjDiscovery) }
    val djHeading = if (djActive) mediaMetadata?.title.orEmpty() else mood?.title ?: stringResource(dayPart.setTitle)
    val djLine =
        if (djActive) {
            listOfNotNull(
                mediaMetadata?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
                discovery?.let { stringResource(if (it) R.string.dj_new_find else R.string.dj_from_favourites) },
            ).joinToString(" · ")
        } else {
            stringResource(djMode.description)
        }

    // ---- For you
    val likedTitle = stringResource(R.string.liked)
    val quickPicksTitle = stringResource(R.string.quick_picks)
    val quickItems =
        remember(recents, keepListening, likedTitle) { quickAccessItems(recents, keepListening.orEmpty(), likedTitle) }
    val mixes =
        buildList {
            daylist?.let { list ->
                add(Mix(stringResource(list.part.titleRes), stringResource(R.string.home_mix_daylist_sub), list.songs.map { it.toMediaItem() }, list.songs.map { it.song.thumbnailUrl }))
            }
            onRepeat?.takeIf { it.size >= MIN_MIX }?.let { songs ->
                add(Mix(stringResource(R.string.home_mix_on_repeat), stringResource(R.string.home_mix_on_repeat_sub), songs.map { it.toMediaItem() }, songs.map { it.song.thumbnailUrl }))
            }
            discoverMix?.takeIf { it.size >= MIN_MIX }?.let { songs ->
                add(Mix(stringResource(R.string.home_mix_discover), stringResource(R.string.home_mix_discover_sub), songs.map { it.toMediaItem() }, songs.map { it.thumbnail }))
            }
            forgotten?.takeIf { it.size >= MIN_MIX }?.let { songs ->
                add(Mix(stringResource(R.string.home_mix_forgotten), stringResource(R.string.home_mix_forgotten_sub), songs.map { it.toMediaItem() }, songs.map { it.song.thumbnailUrl }))
            }
        }
    val releaseArtists = remember(newReleases) { newReleases.orEmpty().flatMap { album -> album.artists.orEmpty().mapNotNull { it.id } }.toHashSet() }

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
            if (moods.isNotEmpty()) {
                item(key = "moods", contentType = "moods") {
                    MoodChips(moods = moods, selected = mood?.title, onSelect = pickMood)
                }
            }

            shown.forEach { block ->
                val feedOnly = selectedChip != null && block != HomeBlock.DJ
                if (feedOnly) return@forEach
                when (block) {
                    HomeBlock.DJ ->
                        item(key = "dj", contentType = "dj") {
                            DjHero(
                                active = djActive,
                                isPlaying = isPlaying,
                                starting = djStarting,
                                title = djHeading,
                                subtitle = djLine,
                                palette = palette,
                                previews = previews,
                                liked = currentSong?.song?.liked == true,
                                onPlay = {
                                    if (djActive) {
                                        playerConnection.togglePlayPause()
                                    } else if (!djStarting) {
                                        djStarting = true
                                        playerConnection.playQueue(viewModel.djQueue(djTitle))
                                    }
                                },
                                onTune = { tuning = true },
                                onDislike = { playerConnection.service.dislikeInDj() },
                                onFavour = {
                                    if (currentSong?.song?.liked == true) playerConnection.toggleLike() else playerConnection.service.favourInDj()
                                },
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).animateItem(),
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
                                    modifier = Modifier.padding(top = 8.dp).animateItem(),
                                )
                            }
                        }

                    HomeBlock.CONTINUE ->
                        if (inProgress.isNotEmpty()) {
                            item(key = "continue_title") {
                                SectionHeader(
                                    title = stringResource(R.string.home_block_continue),
                                    subtitle = stringResource(R.string.home_continue_sub),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "continue", contentType = "continue") {
                                SnappingRow(inProgress, key = { it.albumId }, itemWidth = 128.dp) { progress ->
                                    ContinueCard(
                                        progress = progress,
                                        onContinue = {
                                            scope.launch {
                                                viewModel.continueQueue(progress)?.let(playerConnection::playQueue)
                                                    ?: navController.navigate("album/${progress.albumId}")
                                            }
                                        },
                                        onOpen = { navController.navigate("album/${progress.albumId}") },
                                    )
                                }
                            }
                        }

                    HomeBlock.FOR_YOU ->
                        if (mixes.isNotEmpty()) {
                            item(key = "for_you_title") { SectionHeader(title = stringResource(R.string.home_block_for_you), modifier = Modifier.animateItem()) }
                            item(key = "for_you", contentType = "for_you") {
                                SnappingRow(mixes, key = { it.title }, itemWidth = 146.dp) { mix ->
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

                    HomeBlock.QUICK_PICKS ->
                        quickPicks?.takeIf { it.isNotEmpty() }?.let { songs ->
                            val title = quickPicksTitle
                            item(key = "quick_picks_title") {
                                SectionHeader(
                                    title = title,
                                    action = stringResource(R.string.home_play_all),
                                    onAction = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        playerConnection.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }))
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            itemsIndexed(songs.take(QUICK_PICK_ROWS), key = { _, song -> "pick_${song.id}" }) { index, song ->
                                QuickPickRow(
                                    song = song,
                                    active = song.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    onPlay = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        playerConnection.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = index))
                                    },
                                    onMenu = { menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) } },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                    HomeBlock.ARTISTS ->
                        if (artists.isNotEmpty()) {
                            item(key = "artists_title") { SectionHeader(title = stringResource(R.string.home_block_artists), modifier = Modifier.animateItem()) }
                            item(key = "artists", contentType = "artists") {
                                SnappingRow(artists, key = { it.id }, itemWidth = 84.dp) { artist ->
                                    ArtistCircle(
                                        artist = artist,
                                        hasNew = artist.id in releaseArtists,
                                        onOpen = { navController.navigate("artist/${artist.id}") },
                                    )
                                }
                            }
                        }

                    HomeBlock.NEW_RELEASES ->
                        newReleases?.takeIf { it.isNotEmpty() }?.let { albums ->
                            item(key = "releases_title") {
                                SectionHeader(
                                    title = stringResource(R.string.home_block_new_releases),
                                    subtitle = stringResource(R.string.home_releases_label),
                                    onClick = { navController.navigate("new_release") },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "releases", contentType = "releases") {
                                SnappingRow(albums.take(RELEASES), key = { it.id }, itemWidth = 128.dp) { album ->
                                    ReleaseCard(album = album, onOpen = { navController.navigate("album/${album.browseId}") })
                                }
                            }
                        }

                    HomeBlock.CHART ->
                        chart?.takeIf { it.isNotEmpty() }?.let { songs ->
                            item(key = "chart_title") {
                                SectionHeader(
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
                }
            }

            if (selectedChip != null) {
                homePage?.sections.orEmpty().forEachIndexed { index, section ->
                    item(key = "mood_title_$index") { SectionHeader(title = section.title, subtitle = section.label, modifier = Modifier.animateItem()) }
                    item(key = "mood_$index", contentType = "mood_rail") {
                        SnappingRow(section.items, key = { it.id }, itemWidth = 136.dp) { item ->
                            FeedCard(
                                item = item,
                                onOpen = {
                                    when (item) {
                                        is SongItem -> playerConnection.playQueue(YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                        is PodcastItem -> navController.navigate("online_podcast/${item.id}")
                                        is EpisodeItem -> playerConnection.playQueue(ListQueue(title = item.title, items = listOf(item.toMediaMetadata().toMediaItem())))
                                    }
                                },
                                onMenu = { if (item is SongItem) menuState.show { YouTubeSongMenu(song = item, onDismiss = menuState::dismiss) } },
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

    if (tuning) {
        DjTuneSheet(
            mode = djMode,
            moods = moods,
            mood = mood,
            onMode = { mode ->
                scope.launch {
                    viewModel.setDjMode(mode).join()
                    retune()
                }
            },
            onMood = pickMood,
            onReset = {
                scope.launch {
                    viewModel.setDjMode(com.metrolist.music.dj.DjMode.MIXED).join()
                    if (mood != null) viewModel.selectMood(null)
                    retune()
                }
            },
            onDismiss = { tuning = false },
        )
    }

    if (editing) {
        EditHomeSheet(
            blocks = blocks,
            hidden = hidden,
            onToggle = { block, show -> viewModel.setHomeBlockHidden(block.id, !show) },
            onReorder = { list -> viewModel.setHomeBlockOrder(list.map { it.id }) },
            onReset = { viewModel.resetHome() },
            onDismiss = { editing = false },
        )
    }
}

private val DayPart.setTitle
    get() =
        when (this) {
            DayPart.MORNING -> R.string.dj_set_morning
            DayPart.DAY -> R.string.dj_set_day
            DayPart.EVENING -> R.string.dj_set_evening
            DayPart.NIGHT -> R.string.dj_set_night
        }

private const val MIN_MIX = 5
private const val RELEASES = 10
private const val PREVIEWS = 4
private const val QUICK_PICK_ROWS = 4
private const val START_TIMEOUT_MS = 15_000L

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

/** A cover with its title and a line under it, the one card every rail of home is made of. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    cover: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val (source, scale) = rememberPress()
    Column(
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
        modifier =
            modifier
                .scale(scale)
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onClick()
                    },
                    onLongClick =
                        onLongClick?.let { long ->
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                long()
                            }
                        },
                ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(if (round) CircleShape else RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) { cover() }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Cover(url: String?) {
    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

// ---------------------------------------------------------------- moods

@Composable
private fun MoodChips(
    moods: List<HomePage.Chip>,
    selected: String?,
    onSelect: (HomePage.Chip?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(null)
                },
                label = { Text(stringResource(R.string.home_mood_all)) },
            )
        }
        items(moods, key = { it.title }) { chip ->
            FilterChip(
                selected = selected == chip.title,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(chip)
                },
                label = { Text(chip.title) },
                leadingIcon = { Icon(painterResource(moodIcon(chip.title)), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

// ---------------------------------------------------------------- quick access

@Immutable
private data class QuickItem(
    val title: String,
    val thumbnail: String?,
    val route: String,
    val round: Boolean = false,
    val liked: Boolean = false,
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
                thumbnail = recent.thumbnail,
                route = recent.route,
                round = recent.kind == RecentCollection.Kind.ARTIST,
                recent = recent,
            )
        }
    val liked = QuickItem(likedTitle, null, "auto_playlist/liked", liked = true)
    val favourites =
        keepListening.mapNotNull { item ->
            when (item) {
                is Album -> QuickItem(item.title, item.thumbnailUrl, "album/${item.id}")
                is Artist -> QuickItem(item.title, item.thumbnailUrl, "artist/${item.id}", round = true)
                else -> null
            }
        }
    return (listOf(liked) + fromRecents + favourites).distinctBy { it.route }.take(QUICK_ACCESS)
}

private const val QUICK_ACCESS = 6
private val LikedGradient = Brush.linearGradient(listOf(Color(0xFFFF8FA3), Color(0xFFC2185B)))

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
                .padding(horizontal = 16.dp),
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
                        .then(if (item.liked) Modifier.background(LikedGradient) else Modifier.background(colors.surfaceContainerHighest)),
            ) {
                when {
                    item.liked -> Icon(painterResource(R.drawable.favorite), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    item.thumbnail != null ->
                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .then(if (item.round) Modifier.padding(7.dp).clip(CircleShape) else Modifier),
                        )
                    else -> Icon(painterResource(R.drawable.queue_music), contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            Text(
                item.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
            )
            if (playing) EqualizerBars(Modifier.padding(end = 12.dp).size(14.dp))
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
        while (true) withFrameMillis { value = (it - origin) * EQ_SPEED }
    }
    Canvas(modifier) {
        val w = size.width / 5
        for (i in 0..2) {
            val h = size.height * (0.35f + 0.65f * abs(sin(phase + i * 1.3f)))
            drawRoundRect(color, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

private const val EQ_SPEED = 0.0048f

// ---------------------------------------------------------------- continue

@Composable
private fun ContinueCard(
    progress: AlbumProgress,
    onContinue: () -> Unit,
    onOpen: () -> Unit,
) {
    val next = progress.trackIndex + 2
    CoverCard(
        title = progress.title,
        subtitle = stringResource(R.string.home_continue_track, next, progress.songCount),
        onClick = onContinue,
        onLongClick = onOpen,
    ) {
        Cover(progress.thumbnailUrl)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((progress.trackIndex + 1f) / progress.songCount)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

// ---------------------------------------------------------------- for you

@Immutable
private data class Mix(
    val title: String,
    val subtitle: String,
    val items: List<androidx.media3.common.MediaItem>,
    val covers: List<String?>,
)

/** A mix: four of its covers in a square, like a playlist in the library, and one tap to play. */
@Composable
private fun MixCard(
    mix: Mix,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    val covers = remember(mix.covers) { mix.covers.filterNotNull().distinct().take(4) }
    val accent = rememberArtworkAccent(covers.firstOrNull()) ?: MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(accent, tween(700), label = "mix tint")
    CoverCard(title = mix.title, subtitle = mix.subtitle, onClick = onPlay) {
        if (covers.size >= 4) {
            Column {
                covers.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f)) {
                        pair.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        } else {
            Cover(covers.firstOrNull())
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White),
        ) {
            if (playing) {
                EqualizerBars(Modifier.size(14.dp))
            } else {
                Icon(painterResource(R.drawable.play), contentDescription = null, tint = lerp(tint, Color.Black, 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- quick picks

@Composable
private fun QuickPickRow(
    song: Song,
    active: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SongListItem(
        song = song,
        isActive = active,
        isPlaying = isPlaying,
        isSwipeable = false,
        trailingContent = {
            IconButton(onClick = onMenu) { Icon(painterResource(R.drawable.more_vert), contentDescription = null) }
        },
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .clickable(onClick = onPlay),
    )
}

// ---------------------------------------------------------------- artists

@Composable
private fun ArtistCircle(
    artist: Artist,
    hasNew: Boolean,
    onOpen: () -> Unit,
) {
    Box {
        CoverCard(title = artist.artist.name, subtitle = null, round = true, onClick = onOpen) { Cover(artist.artist.thumbnailUrl) }
        if (hasNew) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// ---------------------------------------------------------------- releases and moods feed

@Composable
private fun ReleaseCard(
    album: AlbumItem,
    onOpen: () -> Unit,
) {
    CoverCard(title = album.title, subtitle = album.artists.orEmpty().joinToString { it.name }, onClick = onOpen) { Cover(album.thumbnail) }
}

@Composable
private fun FeedCard(
    item: YTItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val subtitle =
        when (item) {
            is SongItem -> item.artists.joinToString { it.name }
            is AlbumItem -> item.artists.orEmpty().joinToString { it.name }
            is PlaylistItem -> item.author?.name
            else -> null
        }
    CoverCard(title = item.title, subtitle = subtitle, round = item is ArtistItem, onClick = onOpen, onLongClick = onMenu.takeIf { item is SongItem }) { Cover(item.thumbnail) }
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
                        ).padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (index == 0 || active) colors.primary else colors.onSurface,
                    modifier = Modifier.width(26.dp),
                )
                Box {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp)),
                    )
                    if (active && isPlaying) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                        ) { EqualizerBars(Modifier.size(16.dp)) }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (active) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        menuState.show { YouTubeSongMenu(song = song, onDismiss = menuState::dismiss) }
                    },
                ) { Icon(painterResource(R.drawable.more_vert), contentDescription = null, tint = colors.onSurfaceVariant) }
            }
        }
    }
}

// ---------------------------------------------------------------- edit home

/** Blocks with a handle to drag them into place and a switch to hide them; the new order is kept on release. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHomeSheet(
    blocks: List<HomeBlock>,
    hidden: Set<String>,
    onToggle: (HomeBlock, Boolean) -> Unit,
    onReorder: (List<HomeBlock>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var list by remember(blocks) { mutableStateOf(blocks) }
    var moved by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
            moved = true
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging && moved) {
            onReorder(list)
            moved = false
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.home_edit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            stringResource(R.string.home_edit_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(state = listState) {
            items(list, key = { it.id }) { block ->
                ReorderableItem(reorderState, key = block.id) { dragging ->
                    val visible = block.id !in hidden
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                .padding(end = 16.dp)
                                .height(56.dp),
                    ) {
                        IconButton(
                            onClick = {},
                            modifier =
                                Modifier.draggableHandle(
                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                    onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                ),
                        ) { Icon(painterResource(R.drawable.drag_handle), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) { Icon(painterResource(block.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        Text(
                            stringResource(block.label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp),
                        )
                        Switch(
                            checked = visible,
                            onCheckedChange = { show ->
                                haptic.performHapticFeedback(if (show) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                onToggle(block, show)
                            },
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reset)) }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.home_done)) }
        }
    }
}

/** An icon that fits a mood's name, in English or Russian; a note for anything else. */
internal fun moodIcon(title: String): Int {
    val name = title.lowercase()
    fun has(vararg words: String) = words.any { it in name }
    return when {
        has("commut", "drive", "road", "дорог", "машин", "поездк") -> R.drawable.directions_car
        has("chill", "relax", "calm", "спок", "расслаб", "чил", "отдых") -> R.drawable.self_improvement
        has("energy", "energ", "энерг", "бодр") -> R.drawable.bolt
        has("workout", "gym", "fitness", "трениров", "спорт") -> R.drawable.fitness_center
        has("focus", "study", "фокус", "концентр", "учёб", "учеб") -> R.drawable.center_focus
        has("dance", "electro", "edm", "танц", "электр") -> R.drawable.nightlife
        has("party", "вечерин", "туса") -> R.drawable.celebration
        has("feel good", "happy", "хорош", "позитив", "радост") -> R.drawable.sentiment_very_satisfied
        has("sad", "груст", "печал") -> R.drawable.water_drop
        has("sleep", "сон", "сна") -> R.drawable.bedtime
        has("romance", "love", "романт", "любов") -> R.drawable.favorite
        has("r&b", "soul", "соул", "hip", "rap", "хип", "рэп", "поп", "pop") -> R.drawable.mic
        has("jazz", "blues", "classic", "джаз", "блюз", "классик", "piano") -> R.drawable.piano
        has("rock", "metal", "рок", "метал", "punk", "панк") -> R.drawable.local_fire_department
        else -> R.drawable.headphones
    }
}
