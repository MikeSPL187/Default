package com.metrolist.music.offline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.DownloadedOnlyKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.PlaylistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.LocalNavController
import com.metrolist.music.utils.rememberPreference

/**
 * The home screen while only music on the device can play: no connection, or the user chose
 * downloads only. Everything here plays at once, without a network.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineHomeScreen(viewModel: OfflineHomeViewModel = hiltViewModel()) {
    val offline = LocalOfflineMode.current
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val (_, setDownloadedOnly) = rememberPreference(DownloadedOnlyKey, false)

    val title = stringResource(if (offline.noNetwork) R.string.offline_title_no_network else R.string.offline_title_downloaded_only)
    val allTitle = stringResource(R.string.offline_all_downloads)
    val likedTitle = stringResource(R.string.liked)

    fun play(
        songs: List<Song>,
        queueTitle: String,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    ) {
        if (songs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = queueTitle,
                items = (if (shuffle) songs.shuffled() else songs).map { it.toMediaItem() },
                startIndex = if (shuffle) 0 else startIndex,
            ),
        )
    }

    val songs = allSongs ?: return
    val rowPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "offline_header") {
            OfflineHeader(
                title = title,
                showReconnectNote = offline.noNetwork,
                canTurnOff = offline.downloadedOnly && !offline.noNetwork,
                hasSongs = songs.isNotEmpty(),
                onTurnOff = { setDownloadedOnly(false) },
                onShuffle = { play(songs, allTitle, shuffle = true) },
                onPlay = { play(songs, allTitle) },
            )
        }

        if (songs.isEmpty()) {
            item(key = "offline_empty") { OfflineEmpty() }
            return@LazyColumn
        }

        recent?.takeIf { it.isNotEmpty() }?.let { recentSongs ->
            item(key = "offline_recent_title") {
                NavigationTitle(title = stringResource(R.string.offline_recent))
            }
            itemsIndexed(recentSongs, key = { _, song -> "offline_recent_${song.id}" }) { index, song ->
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
                                        play(recentSongs, title, startIndex = index)
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

        item(key = "offline_playlists_title") {
            NavigationTitle(title = stringResource(R.string.offline_playlists))
        }
        item(key = "offline_playlists") {
            LazyRow(contentPadding = rowPadding) {
                item(key = "offline_all") {
                    CollectionTile(
                        name = allTitle,
                        songs = songs,
                        onClick = { navController.navigate("auto_playlist/downloaded") },
                    )
                }
                liked?.takeIf { it.isNotEmpty() }?.let { likedSongs ->
                    item(key = "offline_liked") {
                        CollectionTile(
                            name = likedTitle,
                            songs = likedSongs,
                            onClick = { navController.navigate("auto_playlist/liked") },
                        )
                    }
                }
                items(playlists.orEmpty(), key = { "offline_playlist_${it.id}" }) { playlist ->
                    PlaylistGridItem(
                        playlist = playlist,
                        badges = {},
                        modifier =
                            Modifier.combinedClickable(
                                onClick = { navController.navigate("local_playlist/${playlist.id}") },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        PlaylistMenu(playlist = playlist, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                                    }
                                },
                            ),
                    )
                }
            }
        }

        albums?.takeIf { it.isNotEmpty() }?.let { downloadedAlbums ->
            item(key = "offline_albums_title") {
                NavigationTitle(title = stringResource(R.string.offline_albums))
            }
            item(key = "offline_albums") {
                LazyRow(contentPadding = rowPadding) {
                    items(downloadedAlbums, key = { "offline_album_${it.id}" }) { album ->
                        AlbumGridItem(
                            album = album,
                            coroutineScope = coroutineScope,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = { navController.navigate("album/${album.id}") },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show { AlbumMenu(originalAlbum = album, onDismiss = menuState::dismiss) }
                                    },
                                ),
                        )
                    }
                }
            }
        }

        item(key = "offline_bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun OfflineHeader(
    title: String,
    showReconnectNote: Boolean,
    canTurnOff: Boolean,
    hasSongs: Boolean,
    onTurnOff: () -> Unit,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (hasSongs) {
                        Text(
                            text = stringResource(R.string.offline_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canTurnOff) {
                    TextButton(onClick = onTurnOff) { Text(stringResource(R.string.offline_turn_off)) }
                }
            }
            if (showReconnectNote) {
                Text(
                    text = stringResource(R.string.offline_reconnect_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (hasSongs) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Button(
                        onClick = onShuffle,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.shuffle), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.offline_shuffle))
                    }
                    FilledTonalButton(
                        onClick = onPlay,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.offline_play))
                    }
                }
            }
        }
    }
}

/** A playlist tile for a collection that is not a playlist in the library, like the liked songs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionTile(
    name: String,
    songs: List<Song>,
    onClick: () -> Unit,
) {
    PlaylistGridItem(
        playlist =
            Playlist(
                playlist = PlaylistEntity(id = "offline_$name", name = name),
                songCount = songs.size,
                songThumbnails = songs.take(4).map { it.song.thumbnailUrl },
            ),
        badges = {},
        modifier = Modifier.combinedClickable(onClick = onClick),
    )
}

@Composable
private fun OfflineEmpty() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.download),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.offline_empty_title),
            style = MaterialTheme.typography.titleMedium,
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
