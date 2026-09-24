/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.ui.screens.wrapped.components.WrappedBackdrop
import com.metrolist.music.ui.screens.wrapped.components.rememberArtworkAccent
import com.metrolist.music.ui.screens.wrapped.pages.ConclusionPage
import com.metrolist.music.ui.screens.wrapped.pages.PlaylistPage
import com.metrolist.music.ui.screens.wrapped.pages.WrappedIntro
import com.metrolist.music.ui.screens.wrapped.pages.WrappedMinutesScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedMinutesTease
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTop5AlbumsScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTop5ArtistsScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTop5SongsScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTopAlbumScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTopArtistScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTopSongScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTotalAlbumsScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTotalArtistsScreen
import com.metrolist.music.ui.screens.wrapped.pages.WrappedTotalSongsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed class WrappedScreenType {
    object Welcome : WrappedScreenType()

    object MinutesTease : WrappedScreenType()

    object MinutesReveal : WrappedScreenType()

    object TotalSongs : WrappedScreenType()

    object TopSongReveal : WrappedScreenType()

    object Top5Songs : WrappedScreenType()

    object TotalAlbums : WrappedScreenType()

    object TopAlbumReveal : WrappedScreenType()

    object Top5Albums : WrappedScreenType()

    object TotalArtists : WrappedScreenType()

    object TopArtistReveal : WrappedScreenType()

    object Top5Artists : WrappedScreenType()

    object Playlist : WrappedScreenType()

    object Conclusion : WrappedScreenType()
}

@Composable
fun WrappedScreen(period: WrappedPeriod) {
    val context = LocalContext.current
    val manager = remember(period) { provideWrappedManager(context, period) }
    DisposableEffect(manager) { onDispose { manager.dispose() } }

    CompositionLocalProvider(LocalWrappedManager provides manager) {
        WrappedScreenContent()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WrappedScreenContent() {
    val navController = LocalNavController.current
    val onClose: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set("wrapped_seen", true)
        navController.popBackStack()
    }
    BackHandler(onBack = onClose)

    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = LocalWrappedManager.current
    val audioService = remember { WrappedAudioService(view.context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        audioService.pause()
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        audioService.resume()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioService.release()
        }
    }

    val state by manager.state.collectAsStateWithLifecycle()
    // Songs played outside any album leave nothing to show there, so those pages are skipped.
    val showAlbums = !state.isDataReady || state.topAlbums.isNotEmpty()
    val screens =
        remember(showAlbums) {
            buildList {
                add(WrappedScreenType.Welcome)
                add(WrappedScreenType.MinutesTease)
                add(WrappedScreenType.MinutesReveal)
                add(WrappedScreenType.TotalSongs)
                add(WrappedScreenType.TopSongReveal)
                add(WrappedScreenType.Top5Songs)
                if (showAlbums) {
                    add(WrappedScreenType.TotalAlbums)
                    add(WrappedScreenType.TopAlbumReveal)
                    add(WrappedScreenType.Top5Albums)
                }
                add(WrappedScreenType.TotalArtists)
                add(WrappedScreenType.TopArtistReveal)
                add(WrappedScreenType.Top5Artists)
                add(WrappedScreenType.Playlist)
                add(WrappedScreenType.Conclusion)
            }
        }
    val pagerState = rememberPagerState(pageCount = { screens.size })
    val isMuted by audioService.isMuted.collectAsStateWithLifecycle()
    val messageTier = WrappedRepository.tier(state.totalMinutes, state.elapsedDays)
    val messageIndex = rememberSaveable(messageTier) { WrappedRepository.randomIndex(messageTier) }
    val message = WrappedRepository.message(messageTier, messageIndex)
    val minutesText = pluralStringResource(R.plurals.minute, state.totalMinutes.toInt(), state.totalMinutes)
    val messagePair = MessagePair(tease = stringResource(message.tease), reveal = stringResource(message.reveal, minutesText))
    val periodPhrase = remember(manager) { manager.period.phrase(context, manager.now.toLocalDate()) }
    val periodTitle = remember(manager) { manager.period.title(context) }

    LaunchedEffect(manager) {
        manager.prepare()
    }

    LaunchedEffect(pagerState, state.trackMap) {
        if (state.trackMap.isEmpty()) return@LaunchedEffect

        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            val screen = screens.getOrNull(page)
            audioService.playTrack(state.trackMap[screen])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = periodTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { audioService.toggleMute() }) {
                        val icon = if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                        Icon(painterResource(icon), "Mute", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val screen = screens[page]
            // Pages revealing one song, album or artist glow in its artwork's colour, like the player.
            val artwork =
                when (screen) {
                    WrappedScreenType.TopSongReveal -> state.topSongs.firstOrNull()?.thumbnailUrl
                    WrappedScreenType.TopAlbumReveal -> state.topAlbums.firstOrNull()?.thumbnailUrl
                    WrappedScreenType.TopArtistReveal -> state.topArtists.firstOrNull()?.artist?.thumbnailUrl
                    else -> null
                }
            Box(Modifier.fillMaxSize()) {
                WrappedBackdrop(accent = rememberArtworkAccent(artwork) ?: MaterialTheme.colorScheme.primaryContainer)
                Box(Modifier.fillMaxSize().padding(paddingValues)) {
                    when (screen) {
                        is WrappedScreenType.Welcome -> {
                            WrappedIntro(
                                label = state.bigLabel,
                                subtitle = stringResource(R.string.wrapped_intro_subtitle_period, periodPhrase),
                            ) { scope.launch { pagerState.animateScrollToPage(page = 1) } }
                        }

                        is WrappedScreenType.MinutesTease -> {
                            WrappedMinutesTease(
                                messagePair = messagePair,
                                onNavigateForward = { scope.launch { pagerState.animateScrollToPage(page = 2) } },
                                isDataReady = state.isDataReady,
                            )
                        }

                        is WrappedScreenType.MinutesReveal -> {
                            WrappedMinutesScreen(
                                messagePair = messagePair,
                                totalMinutes = state.totalMinutes,
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.MinutesReveal),
                            )
                        }

                        is WrappedScreenType.TotalSongs -> {
                            WrappedTotalSongsScreen(
                                uniqueSongCount = state.uniqueSongCount,
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.TotalSongs),
                            )
                        }

                        is WrappedScreenType.TopSongReveal -> {
                            WrappedTopSongScreen(
                                topSong = state.topSongs.firstOrNull(),
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.TopSongReveal),
                            )
                        }

                        is WrappedScreenType.Top5Songs -> {
                            WrappedTop5SongsScreen(
                                title = stringResource(R.string.wrapped_top_songs_title_period, periodPhrase),
                                topSongs = state.topSongs.take(5),
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.Top5Songs),
                            )
                        }

                        is WrappedScreenType.TotalAlbums -> {
                            WrappedTotalAlbumsScreen(
                                uniqueAlbumCount = state.totalAlbums,
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.TotalAlbums),
                            )
                        }

                        is WrappedScreenType.TopAlbumReveal -> {
                            WrappedTopAlbumScreen(
                                topAlbum = state.topAlbums.firstOrNull(),
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.TopAlbumReveal),
                            )
                        }

                        is WrappedScreenType.Top5Albums -> {
                            WrappedTop5AlbumsScreen(
                                title = stringResource(R.string.wrapped_top_albums_title_period, periodPhrase),
                                topAlbums = state.topAlbums,
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.Top5Albums),
                            )
                        }

                        is WrappedScreenType.TotalArtists -> {
                            WrappedTotalArtistsScreen(
                                uniqueArtistCount = state.uniqueArtistCount,
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.TotalArtists),
                            )
                        }

                        is WrappedScreenType.TopArtistReveal -> {
                            WrappedTopArtistScreen(
                                title = stringResource(R.string.wrapped_top_artist_title_period, periodPhrase),
                                topArtist = state.topArtists.firstOrNull(),
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.TopArtistReveal),
                            )
                        }

                        is WrappedScreenType.Top5Artists -> {
                            WrappedTop5ArtistsScreen(
                                title = stringResource(R.string.wrapped_top_artists_title_period, periodPhrase),
                                topArtists = state.topArtists,
                                isVisible = pagerState.currentPage == screens.indexOf(WrappedScreenType.Top5Artists),
                            )
                        }

                        is WrappedScreenType.Playlist -> {
                            PlaylistPage()
                        }

                        is WrappedScreenType.Conclusion -> {
                            ConclusionPage(onClose = onClose)
                        }
                    }
                }
            }
        }
    }
}
