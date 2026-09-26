package com.metrolist.music.offline

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ui.screens.home.DjHero
import com.metrolist.music.ui.screens.home.spherePalette
import com.metrolist.music.constants.FlowCharacterKey
import com.metrolist.music.constants.FlowModeKey
import com.metrolist.music.constants.OfflineSongSortKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistDownloadCount
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.PlaylistThumbnail
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.PlaylistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.screens.wrapped.components.WrappedAmbience
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent
import com.metrolist.music.utils.rememberEnumPreference
import kotlinx.coroutines.launch

private enum class OfflineTab { TRACKS, PLAYLISTS, ALBUMS, ARTISTS }

/** The item the tabs sit at in the list; they stay pinned under the top bar once scrolled past. */
private const val TABS_INDEX = 2

/**
 * The home screen while only music on the device can play: no connection, or the user chose
 * downloads only. It opens on the flow, an endless mix of the downloads tuned to the user's taste,
 * and below it holds everything on the device.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineHomeScreen(viewModel: OfflineHomeViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()

    val library by viewModel.library.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloadCounts by viewModel.downloadCounts.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val likedTotal by viewModel.likedTotal.collectAsStateWithLifecycle()

    var mode by rememberEnumPreference(FlowModeKey, FlowMode.ALL)
    var character by rememberEnumPreference(FlowCharacterKey, FlowCharacter.BALANCED)
    var sort by rememberEnumPreference(OfflineSongSortKey, OfflineSongSort.RECENTLY_PLAYED)
    var tab by rememberSaveable { mutableStateOf(OfflineTab.TRACKS) }

    val flowTitle = stringResource(R.string.offline_flow_title)
    val allTitle = stringResource(R.string.offline_all_downloads)
    val likedTitle = stringResource(R.string.liked)
    val flowIsQueued = queueTitle == flowTitle

    val offlineLibrary = library ?: return
    if (offlineLibrary.songs.isEmpty()) {
        OfflineEmpty(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current))
        return
    }

    fun play(
        songs: List<Song>,
        title: String,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    ) {
        if (songs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = (if (shuffle) songs.shuffled() else songs).map { it.toMediaItem() },
                startIndex = if (shuffle) 0 else startIndex,
            ),
        )
    }

    fun startFlow(
        flowMode: FlowMode = mode,
        flowCharacter: FlowCharacter = character,
    ) = play(offlineLibrary.flow(flowMode, flowCharacter), flowTitle)

    val sortedSongs = remember(offlineLibrary, sort) { offlineLibrary.sorted(sort) }
    val likedSongs = remember(offlineLibrary) { offlineLibrary.songs.filter { it.song.liked } }
    val tabs =
        listOf(
            OfflineTab.TRACKS to stringResource(R.string.offline_tab_tracks, offlineLibrary.songs.size),
            OfflineTab.PLAYLISTS to stringResource(R.string.offline_tab_playlists, playlists.orEmpty().size + if (likedSongs.isEmpty()) 0 else 1),
            OfflineTab.ALBUMS to stringResource(R.string.offline_tab_albums, albums.orEmpty().size),
            OfflineTab.ARTISTS to stringResource(R.string.offline_tab_artists, artists.orEmpty().size),
        )

    val listState = rememberLazyListState()
    val tabsPinned by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > TABS_INDEX ||
                (listState.firstVisibleItemIndex == TABS_INDEX && listState.firstVisibleItemScrollOffset > 0)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "flow_hero") {
                FlowHero(
                    count = offlineLibrary.songs.size,
                    active = flowIsQueued,
                    playing = flowIsQueued && isPlaying,
                    current = mediaMetadata,
                    currentLiked = currentSong?.song?.liked == true,
                    previews = remember(offlineLibrary.songs) { offlineLibrary.songs.mapNotNull { it.song.thumbnailUrl }.distinct().shuffled().take(4) },
                    onPlay = { if (flowIsQueued) playerConnection.togglePlayPause() else startFlow() },
                    onSkip = playerConnection::seekToNext,
                    onLike = playerConnection::toggleLike,
                )
            }
            item(key = "flow_tuning") {
                FlowTuning(
                    mode = mode,
                    counts = offlineLibrary.modeCounts,
                    character = character,
                    onMode = {
                        mode = it
                        if (flowIsQueued) startFlow(flowMode = it)
                    },
                    onCharacter = {
                        character = it
                        if (flowIsQueued) startFlow(flowCharacter = it)
                    },
                )
            }
            item(key = "tabs") {
                ChipsRow(
                    chips = tabs,
                    currentValue = tab,
                    onValueUpdate = { tab = it },
                    modifier = Modifier.padding(vertical = 8.dp).alpha(if (tabsPinned) 0f else 1f),
                )
            }

            when (tab) {
                OfflineTab.TRACKS -> {
                    item(key = "tracks_header") {
                        TracksHeader(
                            sort = sort,
                            onSort = { sort = it },
                            onShuffle = { play(offlineLibrary.songs, allTitle, shuffle = true) },
                        )
                    }
                    itemsIndexed(sortedSongs, key = { _, song -> "track_${song.id}" }) { index, song ->
                        SongListItem(
                            song = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) }
                                    },
                                ) {
                                    Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                play(sortedSongs, allTitle, startIndex = index)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) }
                                        },
                                    ).animateItem(),
                        )
                    }
                }

                OfflineTab.PLAYLISTS -> {
                    if (likedSongs.isNotEmpty()) {
                        item(key = "liked_card") {
                            LikedCard(
                                downloaded = likedSongs.size,
                                total = likedTotal ?: likedSongs.size,
                                onOpen = { navController.navigate("auto_playlist/liked") },
                                onPlay = { play(likedSongs, likedTitle) },
                                onShuffle = { play(likedSongs, likedTitle, shuffle = true) },
                            )
                        }
                    }
                    val ownPlaylists = playlists.orEmpty()
                    if (ownPlaylists.isNotEmpty()) {
                        item(key = "playlists_title") { NavigationTitle(title = stringResource(R.string.offline_my_playlists)) }
                        items(ownPlaylists.chunked(2), key = { row -> "playlists_${row.first().id}" }) { row ->
                            CardRow {
                                row.forEach { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        count = downloadCounts?.get(playlist.id),
                                        onOpen = { navController.navigate("local_playlist/${playlist.id}") },
                                        onMenu = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                PlaylistMenu(playlist = playlist, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                                            }
                                        },
                                        onPlay = {
                                            coroutineScope.launch { play(viewModel.playlistSongs(playlist.id), playlist.playlist.name) }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (likedSongs.isEmpty() && ownPlaylists.isEmpty()) item(key = "playlists_empty") { EmptyTab() }
                }

                OfflineTab.ALBUMS -> {
                    val downloadedAlbums = albums.orEmpty()
                    items(downloadedAlbums.chunked(2), key = { row -> "albums_${row.first().id}" }) { row ->
                        CardRow {
                            row.forEach { album ->
                                AlbumCard(
                                    album = album,
                                    onOpen = { navController.navigate("album/${album.id}") },
                                    onMenu = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show { AlbumMenu(originalAlbum = album, onDismiss = menuState::dismiss) }
                                    },
                                    onPlay = { coroutineScope.launch { play(viewModel.albumSongs(album.id), album.album.title) } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (downloadedAlbums.isEmpty()) item(key = "albums_empty") { EmptyTab() }
                }

                OfflineTab.ARTISTS -> {
                    val downloadedArtists = artists.orEmpty()
                    items(downloadedArtists, key = { "artist_${it.artist.id}" }) { artist ->
                        ArtistRow(
                            artist = artist,
                            onPlay = { play(artist.songs, artist.artist.name, shuffle = true) },
                        )
                    }
                    if (downloadedArtists.isEmpty()) item(key = "artists_empty") { EmptyTab() }
                }
            }
        }

        if (tabsPinned) {
            ChipsRow(
                chips = tabs,
                currentValue = tab,
                onValueUpdate = { tab = it },
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 8.dp),
            )
        }
    }
}

/** The flow as the same living sphere as the DJ on home, fed only by what is on the device. */
@Composable
private fun FlowHero(
    count: Int,
    active: Boolean,
    playing: Boolean,
    current: MediaMetadata?,
    currentLiked: Boolean,
    previews: List<String>,
    onPlay: () -> Unit,
    onSkip: () -> Unit,
    onLike: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = rememberArtworkAccent(if (active) current?.thumbnailUrl else null) ?: colors.primary
    val palette = remember(accent, colors.tertiary) { spherePalette(accent, colors.tertiary) }
    DjHero(
        active = active && current != null,
        isPlaying = playing,
        starting = false,
        title = if (active && current != null) current.title else stringResource(R.string.offline_flow_idle),
        subtitle =
            if (active && current != null) {
                current.artists.joinToString { it.name }
            } else {
                pluralStringResource(R.plurals.offline_flow_label, count, count)
            },
        palette = palette,
        previews = previews,
        liked = currentLiked,
        onPlay = onPlay,
        onTune = {},
        onDislike = onSkip,
        onFavour = onLike,
        label = stringResource(R.string.offline_flow_title),
        liveLabel = stringResource(R.string.offline_flow_live),
        showTune = false,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

private data class ModeLook(
    val label: Int,
    val icon: Int,
    val color: Color,
)

private val FlowMode.look: ModeLook
    get() =
        when (this) {
            FlowMode.ALL -> ModeLook(R.string.offline_mode_all, R.drawable.graphic_eq, Color(0xFF3D63C9))
            FlowMode.LIKED -> ModeLook(R.string.offline_mode_liked, R.drawable.favorite, Color(0xFFB83A72))
            FlowMode.FORGOTTEN -> ModeLook(R.string.offline_mode_forgotten, R.drawable.history, Color(0xFF9A6A16))
            FlowMode.NEW -> ModeLook(R.string.offline_mode_new, R.drawable.download, Color(0xFF1F8A68))
        }

@Composable
private fun FlowTuning(
    mode: FlowMode,
    counts: Map<FlowMode, Int>,
    character: FlowCharacter,
    onMode: (FlowMode) -> Unit,
    onCharacter: (FlowCharacter) -> Unit,
) {
    Column {
        NavigationTitle(title = stringResource(R.string.offline_flow_tune))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(FlowMode.entries, key = { it.name }) { option ->
                ModeCard(
                    mode = option,
                    count = counts[option] ?: 0,
                    selected = option == mode,
                    onClick = { onMode(option) },
                )
            }
        }
        Text(
            text = stringResource(R.string.offline_character),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        val characters =
            listOf(
                FlowCharacter.FAMILIAR to R.string.offline_character_familiar,
                FlowCharacter.BALANCED to R.string.offline_character_balanced,
                FlowCharacter.RARE to R.string.offline_character_rare,
            )
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            characters.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = option == character,
                    onClick = { onCharacter(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = characters.size),
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ModeCard(
    mode: FlowMode,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val look = mode.look
    val enabled = count > 0
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier =
            Modifier
                .width(136.dp)
                .height(96.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(look.color, lerpToWhite(look.color, 0.28f))))
                .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(14.dp),
    ) {
        Icon(painterResource(look.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(stringResource(look.label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                pluralStringResource(R.plurals.n_song, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

private fun lerpToWhite(
    color: Color,
    amount: Float,
) = Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount,
)

@Composable
private fun TracksHeader(
    sort: OfflineSongSort,
    onSort: (OfflineSongSort) -> Unit,
    onShuffle: () -> Unit,
) {
    val sorts =
        listOf(
            OfflineSongSort.RECENTLY_PLAYED to R.string.offline_sort_recently_played,
            OfflineSongSort.RECENTLY_DOWNLOADED to R.string.offline_sort_recently_downloaded,
            OfflineSongSort.NAME to R.string.offline_sort_name,
        )
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 16.dp),
    ) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { menuOpen = true }) {
                Text(stringResource(sorts.first { it.first == sort }.second))
                Icon(painterResource(R.drawable.expand_more), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                sorts.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            onSort(option)
                            menuOpen = false
                        },
                        trailingIcon = { if (option == sort) Icon(painterResource(R.drawable.check), contentDescription = null) },
                    )
                }
            }
        }
        FilledTonalButton(onClick = onShuffle, contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(painterResource(R.drawable.shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.offline_shuffle))
        }
    }
}

@Composable
private fun LikedCard(
    downloaded: Int,
    total: Int,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF5B2C8F), Color(0xFFB83A72), Color(0xFFE0709A))))
                .clickable(onClick = onOpen)
                .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.favorite), contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.liked), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text =
                        if (downloaded < total) {
                            stringResource(R.string.offline_downloaded_of, downloaded, total)
                        } else {
                            pluralStringResource(R.plurals.n_song, downloaded, downloaded)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3A1030)),
                contentPadding = LikedButtonPadding,
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(6.dp))
                FittingLabel(stringResource(R.string.offline_play))
            }
            OutlinedButton(
                onClick = onShuffle,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = LikedButtonPadding,
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.shuffle), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(6.dp))
                FittingLabel(stringResource(R.string.offline_shuffle))
            }
        }
    }
}

private val LikedButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

/** A button label that shrinks a little rather than being cut off with large system fonts. */
@Composable
private fun FittingLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
    )
}

@Composable
private fun CardRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

/** A square cover with a play button on it, the title under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    cover: @Composable (size: androidx.compose.ui.unit.Dp) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.combinedClickable(onClick = onOpen, onLongClick = onMenu)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            cover(maxWidth)
            if (badge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .background(colors.surface.copy(alpha = 0.85f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(painterResource(R.drawable.download), contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                    Text(badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
            FilledIconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp),
            ) {
                Icon(painterResource(R.drawable.play), contentDescription = stringResource(R.string.play), modifier = Modifier.size(22.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    count: PlaylistDownloadCount?,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = count?.total ?: playlist.songCount
    val downloaded = count?.downloaded ?: total
    val partial = downloaded < total
    CoverCard(
        title = playlist.playlist.name,
        subtitle =
            if (partial) {
                stringResource(R.string.offline_downloaded_of, downloaded, total)
            } else {
                pluralStringResource(R.plurals.n_song, total, total)
            },
        badge = if (partial) "$downloaded/$total" else null,
        onOpen = onOpen,
        onMenu = onMenu,
        onPlay = onPlay,
        modifier = modifier,
    ) { size ->
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = size,
            placeHolder = {
                Icon(painterResource(R.drawable.queue_music), contentDescription = null, modifier = Modifier.size(size / 3))
            },
            shape = RoundedCornerShape(0.dp),
        )
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        title = album.album.title,
        subtitle = album.artists.joinToString { it.name },
        onOpen = onOpen,
        onMenu = onMenu,
        onPlay = onPlay,
        modifier = modifier,
    ) {
        AsyncImage(
            model = album.album.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ArtistRow(
    artist: OfflineArtist,
    onPlay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = artist.artist.thumbnailUrl ?: artist.songs.first().song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(artist.artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.n_song, artist.songs.size, artist.songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(painterResource(R.drawable.shuffle), contentDescription = stringResource(R.string.offline_shuffle), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyTab() {
    Text(
        text = stringResource(R.string.offline_empty_tab),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
    )
}

@Composable
private fun OfflineEmpty(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.download),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.offline_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.offline_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Sits in the top bar while the app works from the device: says there is no connection, or, when
 * the user chose downloads only, offers to turn that off.
 */
@Composable
fun OfflineStatusChip(
    offline: OfflineMode,
    onTurnOff: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val canTurnOff = !offline.noNetwork && offline.downloadedOnly
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHighest,
        onClick = onTurnOff,
        enabled = canTurnOff,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(painterResource(R.drawable.cloud_off), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(if (offline.noNetwork) R.string.offline_status_no_network else R.string.offline_status_downloads),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (canTurnOff) {
                Icon(
                    painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.offline_turn_off),
                    tint = colors.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(start = 4.dp)
                            .size(16.dp),
                )
            }
        }
    }
}
