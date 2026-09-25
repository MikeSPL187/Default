/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import com.metrolist.music.utils.RecentCollections
import com.metrolist.music.utils.RecentCollection
import com.metrolist.music.dj.DjQueue
import com.metrolist.music.constants.HiddenHomeBlocksKey
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.music.utils.Daylist
import com.metrolist.music.utils.DayPart
import com.metrolist.music.utils.filterNotRecommended
import com.metrolist.music.utils.notRecommended
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.constants.ShowWrappedCardKey
import com.metrolist.music.constants.WrappedSeenYearKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.filterExplicit
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.SimilarRecommendation
import com.metrolist.music.ui.screens.wrapped.WRAPPED_MIN_PLAY_TIME_MS
import com.metrolist.music.ui.screens.wrapped.WrappedPeriod
import com.metrolist.music.ui.screens.wrapped.range
import com.metrolist.music.ui.screens.wrapped.wrappedSeasonYear
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.read
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

internal fun buildSpeedDialItems(
    pinned: List<YTItem>,
    keepListening: List<YTItem>,
    quickPicks: List<YTItem>,
    home: List<YTItem>,
): List<YTItem> =
    (pinned + keepListening + quickPicks + home)
        .distinctBy { it.id }
        .take(27)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    /** New albums, those by artists the user listens to first. */
    val newReleases = MutableStateFlow<List<AlbumItem>?>(null)

    /** The top songs of the charts, for the home screen. */
    val chart = MutableStateFlow<List<SongItem>?>(null)

    /** Songs related to what the user likes, for the discoveries mix. */
    val discoverMix = MutableStateFlow<List<SongItem>?>(null)

    /** What the user played most this month. */
    val onRepeat = MutableStateFlow<List<Song>?>(null)

    /** Collections the user opened lately, newest first, for quick access. */
    val recentCollections =
        RecentCollections.flow(context).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Home blocks the user chose to hide. */
    val hiddenHomeBlocks =
        context.dataStore.data.map { it[HiddenHomeBlocksKey].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setHomeBlockHidden(
        block: String,
        hidden: Boolean,
    ) = viewModelScope.launch {
        context.safeDataStoreEdit { prefs ->
            val current = prefs[HiddenHomeBlocksKey].orEmpty()
            prefs[HiddenHomeBlocksKey] = if (hidden) current + block else current - block
        }
    }

    fun forgetRecent(item: RecentCollection) = viewModelScope.launch { RecentCollections.remove(context, item) }

    fun djQueue(title: String) = DjQueue(title, database, context)

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val daylist = MutableStateFlow<Daylist?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // Official API data for podcast sections
    val savedPodcastShows = MutableStateFlow<List<com.metrolist.innertube.models.PodcastItem>>(emptyList())
    val episodesForLater = MutableStateFlow<List<SongItem>>(emptyList())

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val pinnedSpeedDialItems: StateFlow<List<SpeedDialItem>> =
        database.speedDialDao.getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks,
            homePage,
        ) { pinned, keepListening, quick, home ->
            buildSpeedDialItems(
                pinned = pinned.map { it.toYTItem() },
                keepListening =
                    keepListening.orEmpty().mapNotNull { item ->
                        when (item) {
                            is Song ->
                                SongItem(
                                    id = item.id,
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is Album ->
                                AlbumItem(
                                    browseId = item.id,
                                    playlistId = item.album.playlistId ?: "",
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    year = item.album.year,
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is com.metrolist.music.db.entities.Artist ->
                                ArtistItem(
                                    id = item.id,
                                    title = item.title,
                                    thumbnail = item.thumbnailUrl,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )

                            else -> null
                        }
                    },
                quickPicks =
                    quick.orEmpty().map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                        )
                    },
                home = home?.sections.orEmpty().flatMap { it.items },
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        is com.metrolist.music.db.entities.Artist -> otherSources.add(ArtistItem(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnailUrl,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            // Probability: 80% User Songs, 20% Other Sources
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    /**
     * The year whose recap the home card offers: only in December and January, only after at least
     * a minute of listening that year, and until it is seen unless the card is kept in settings.
     */
    val wrappedCardYear: StateFlow<Int?> =
        context.dataStore.data
            .map { prefs ->
                val year = wrappedSeasonYear(LocalDate.now()) ?: return@map null
                val keepShowing = prefs[ShowWrappedCardKey] ?: false
                year.takeIf { keepShowing || prefs[WrappedSeenYearKey] != year }
            }.distinctUntilChanged()
            .map { year ->
                year?.takeIf {
                    val range = WrappedPeriod.InYear(it).range(LocalDateTime.now())
                    (database.getTotalPlayTimeInRange(range.from, range.to).first() ?: 0L) >= WRAPPED_MIN_PLAY_TIME_MS
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun markWrappedAsSeen(year: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            context.safeDataStoreEdit {
                it[WrappedSeenYearKey] = year
            }
        }
    }

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)
        
        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())
        val mix = java.util.Collections.synchronizedList(mutableListOf<SongItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            mix += recommendations.filter { it.id != seed.id }.take(DISCOVER_PER_SEED)

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }
        
        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
        val liked = likedSongs.mapTo(HashSet()) { it.id }
        discoverMix.value =
            mix.toList().distinctBy { it.id }.filterNot { it.id in liked }
                .filterNotRecommended(context.notRecommended()).shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
        val hideExplicit = context.dataStore.read(HideExplicitKey, false)
        val notRecommended = context.notRecommended()
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs).filterExplicit(hideExplicit)
                    .filterNotRecommended(notRecommended)
                val forgotten =
                    database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).filterExplicit(hideExplicit)
                        .filterNotRecommended(notRecommended).take(8)

                // Get similar songs from YouTube based on recent listening
                val recentSong = database.latestEvent().first()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if ((!hideVideoSongs || !localSong.song.isVideo) && (!hideExplicit || !localSong.song.explicit) &&
                                        !notRecommended.blocks(localSong.id, localSong.artists.map { it.id })
                                    ) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combine all sources and remove duplicates
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(20) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.latestEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value =
                        database.getRelatedSongs(song.id).first()
                            .filterVideoSongs(hideVideoSongs)
                            .filterExplicit(hideExplicit)
                            .filterNotRecommended(notRecommended)
                            .shuffled()
                            .take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = LocalDateTime.now().minusWeeks(4)
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 5, offset = 0, toTimeStamp = LocalDateTime.now()).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
            
            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    private suspend fun load() {
        isLoading.value = true
        val hideExplicit = context.dataStore.read(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
        val fromTimeStamp = LocalDateTime.now().minusWeeks(2)

        // Phase 1: Load essential sections in parallel — local DB (fast) + YouTube home page.
        // isLoading is set to false as soon as all Phase 1 tasks complete so the UI appears quickly.
        coroutineScope {
            launch(Dispatchers.IO) { getQuickPicks() }

            launch(Dispatchers.IO) {
                onRepeat.value =
                    database.mostPlayedSongs(LocalDateTime.now().minusDays(ON_REPEAT_DAYS), limit = ON_REPEAT_SIZE).first()
                        .filterNot { it.song.isEpisode }
                        .filterVideoSongs(hideVideoSongs).filterExplicit(hideExplicit)
                        .filterNotRecommended(context.notRecommended())
                        .shuffled()
            }

            launch(Dispatchers.IO) {
                forgottenFavorites.value = database.forgottenFavorites().first()
                    .filterVideoSongs(hideVideoSongs).filterExplicit(hideExplicit).shuffled().take(20)
            }

            launch(Dispatchers.IO) {
                val part = DayPart.now()
                // A wider pool than shown, so the list changes from day to day.
                val songs = database.songsPlayedAtHours(part.hours, LocalDateTime.now().minusDays(DAYLIST_DAYS), DAYLIST_POOL)
                    .filterNot { it.song.isEpisode }
                    .filterVideoSongs(hideVideoSongs)
                    .filterExplicit(hideExplicit)
                    .filterNotRecommended(context.notRecommended())
                    .shuffled()
                    .take(DAYLIST_SIZE)
                daylist.value = if (songs.size >= DAYLIST_MIN_SIZE) Daylist(part, songs) else null
            }

            launch(Dispatchers.IO) {
                val songs = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 5, toTimeStamp = LocalDateTime.now()).first()
                    .filterVideoSongs(hideVideoSongs).filterExplicit(hideExplicit).shuffled().take(10)
                val albums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
                    .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                val artists = database.mostPlayedArtists(fromTimeStamp).first()
                    .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
                keepListening.value = (songs + albums + artists).shuffled()
            }

            launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    homePage.value = page.copy(
                        sections = page.sections.mapNotNull { section ->
                            val filtered = section.items
                                .filterOutNulls()
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts).filterNotRecommended(context.notRecommended())
                            if (filtered.isEmpty()) null else section.copy(items = filtered)
                        }
                    )
                }.onFailure { reportException(it) }
            }

            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountInfo() }
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
        isLoading.value = false

        // Phase 2: Heavy multi-request operations — run in background without blocking the UI.
        viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }

        viewModelScope.launch(Dispatchers.IO) { getCommunityPlaylists() }

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.explore().onSuccess { page ->
                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums.filterOutNulls().filterExplicit(hideExplicit),
                    moodAndGenres = page.moodAndGenres.filterOutNulls()
                )
                // Releases by artists the user listens to come first.
                val listened = database.mostPlayedArtists(LocalDateTime.now().minusMonths(6), limit = 100).first().mapTo(HashSet()) { it.id }
                newReleases.value =
                    page.newReleaseAlbums.filterOutNulls().filterExplicit(hideExplicit)
                        .sortedByDescending { album -> album.artists.orEmpty().any { it.id in listened } }
            }.onFailure { reportException(it) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.getChartsPage().onSuccess { page ->
                val section =
                    page.sections.firstOrNull { it.chartType == ChartsPage.ChartType.TOP && it.items.any { item -> item is SongItem } }
                        ?: page.sections.firstOrNull { it.items.any { item -> item is SongItem } }
                chart.value =
                    section?.items.orEmpty().filterIsInstance<SongItem>()
                        .filterExplicit(hideExplicit)
                        .filterNotRecommended(context.notRecommended())
                        .take(HOME_CHART_SIZE)
            }.onFailure { Timber.w(it, "Could not load the chart for home") }
            // Charts are not published in every country; the global chart stands in for them.
            if (chart.value.orEmpty().size < HOME_CHART_SIZE) {
                YouTube.playlist(GLOBAL_CHART_PLAYLIST).onSuccess { page ->
                    chart.value =
                        page.songs.filterExplicit(hideExplicit)
                            .filterNotRecommended(context.notRecommended())
                            .take(HOME_CHART_SIZE)
                }.onFailure { Timber.w(it, "Could not load the global chart for home") }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        page.sections.takeLast(3).forEach { section -> items += section.items }
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .distinctBy { item -> item.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(12)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val songRecommendations = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 0, toTimeStamp = LocalDateTime.now()).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(10) +
                                page.albums.shuffled().take(5) +
                                page.artists.shuffled().take(3) +
                                page.playlists.shuffled().take(3))
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val albumRecommendations = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .mapNotNull { album ->
                    val items = mutableListOf<YTItem>()
                    YouTube.album(album.id).onSuccess { page ->
                        page.otherVersions.let { items += it }
                    }
                    album.artists.firstOrNull()?.id?.let { artistId ->
                        YouTube.artist(artistId).onSuccess { page ->
                            page.sections.lastOrNull()?.items?.let { items += it }
                        }
                    }
                    SimilarRecommendation(
                        title = album,
                        items = items
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(10)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            similarRecommendations.value = (artistRecommendations + songRecommendations + albumRecommendations).shuffled()
            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value?.sections?.flatMap { it.items }.orEmpty()
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        // Set before launching, so a second scroll event cannot start the same page again.
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hideExplicit = context.dataStore.read(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
                val nextSections = YouTube.home(continuation).getOrNull() ?: return@launch

                homePage.value = nextSections.copy(
                    chips = homePage.value?.chips,
                    sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                        val filteredItems = section.items
                            .filterOutNulls()
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts).filterNotRecommended(context.notRecommended())
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }
                )
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.read(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.mapNotNull { section ->
                    section.copy(items = section.items.filterOutNulls().filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts).filterNotRecommended(context.notRecommended()))
                }
            )
            selectedChip.value = chip

            // Fetch podcast-specific data when podcasts chip is selected
            if (chip.title.contains("Podcast", ignoreCase = true)) {
                fetchPodcastData()
            }
        }
    }

    private suspend fun fetchPodcastData() {
        // Fetch saved podcast shows from official API
        YouTube.savedPodcastShows().onSuccess { shows ->
            savedPodcastShows.value = shows.filterOutNulls()
        }.onFailure {
            reportException(it)
        }

        // Fetch episodes for later from official API
        YouTube.episodesForLater().onSuccess { episodes ->
            episodesForLater.value = episodes.filterOutNulls()
        }.onFailure {
            reportException(it)
        }
    }

    private suspend fun loadAccountInfo() {
        YouTube.accountInfo().onSuccess { info ->
            accountName.value = info.name
            accountImageUrl.value = info.thumbnailUrl
        }.onFailure {
            reportException(it)
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterOutNulls()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    /**
     * Safely filters out null items from a list whose type says non-null
     * but may contain nulls at runtime due to JSON parsing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> List<T>.filterOutNulls(): List<T> =
        (this as List<T?>).filterNotNull()

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // If a chip is selected, reload the chip's content instead of the default home
            val currentChip = selectedChip.value
            if (currentChip != null) {
                val hideExplicit = context.dataStore.read(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.read(HideYoutubeShortsKey, false)
                val nextSections = YouTube.home(params = currentChip.endpoint?.params).getOrNull()
                if (nextSections != null) {
                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips,
                        sections = nextSections.sections.mapNotNull { section ->
                            section.copy(items = section.items.filterOutNulls().filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts).filterNotRecommended(context.notRecommended()))
                        }
                    )
                }
            } else {
                load()
            }
            isRefreshing.value = false
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {
        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        var wasOffline = !networkConnectivity.networkStatus.value
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectivity.networkStatus.collect { isConnected ->
                if (!isConnected) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    refresh()
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] to it[AccountNameKey] }
                .distinctUntilChanged()
                .collect { (cookie, savedAccountName) ->
                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie
                        accountName.value = savedAccountName.orEmpty().ifBlank { "Guest" }
                        loadAccountInfo()
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }

    private var isHomeDataLoaded = false

    fun loadHomeData() {
        if (isHomeDataLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                if (!cookie.isNullOrEmpty()) {
                    YouTube.cookie = cookie
                }

                isHomeDataLoaded = true
                load()
            } catch (e: Exception) {
                isHomeDataLoaded = false
                Timber.e(e, "Failed to load home data")
            }
        }
    }
}

private const val DAYLIST_DAYS = 60L
private const val DAYLIST_POOL = 40
private const val DAYLIST_SIZE = 25
private const val DAYLIST_MIN_SIZE = 8

private const val HOME_CHART_SIZE = 5
private const val GLOBAL_CHART_PLAYLIST = "PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"
private const val DISCOVER_PER_SEED = 5
private const val ON_REPEAT_DAYS = 30L
private const val ON_REPEAT_SIZE = 30
