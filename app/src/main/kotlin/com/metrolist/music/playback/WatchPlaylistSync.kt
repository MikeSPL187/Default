package com.metrolist.music.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.metrolist.music.constants.DownloadOnWifiOnlyKey
import com.metrolist.music.constants.WatchSyncPlaylistIdsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.di.ApplicationScope
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the watch folder in step with the playlists the user chose to sync: songs added to those
 * playlists are exported, and songs the sync exported are deleted again once no synced playlist
 * holds them. Songs the user exported by hand are never deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class WatchPlaylistSync
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val watchExportManager: WatchExportManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val started = AtomicBoolean(false)

    val syncedPlaylistIds: Flow<Set<String>> =
        context.dataStore.data.map { it[WatchSyncPlaylistIdsKey].orEmpty() }.distinctUntilChanged()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) {
            val wifiOnly = context.dataStore.data.map { it[DownloadOnWifiOnlyKey] ?: false }.distinctUntilChanged()
            syncedPlaylistIds
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(ids.map { database.playlistSongs(it) }) { playlists ->
                            playlists.flatMap { songs -> songs.map { it.song } }
                        }
                    }
                }
                .combine(combine(wifiOnly, unmeteredNetwork()) { onlyWifi, unmetered -> !onlyWifi || unmetered }) { songs, mayUseNetwork ->
                    songs to mayUseNetwork
                }
                // Playlist edits arrive in bursts; wait for them to settle before touching files.
                .debounce(SETTLE_DELAY_MS)
                .collectLatest { (songs, mayUseNetwork) -> sync(songs, mayUseNetwork) }
        }
    }

    suspend fun setPlaylistSynced(playlistId: String, synced: Boolean) {
        context.safeDataStoreEdit { prefs ->
            val ids = prefs[WatchSyncPlaylistIdsKey].orEmpty()
            prefs[WatchSyncPlaylistIdsKey] = if (synced) ids + playlistId else ids - playlistId
        }
    }

    private suspend fun sync(songs: List<Song>, mayUseNetwork: Boolean) {
        val wanted = songs.filterNot { it.song.isEpisode }.distinctBy { it.id }
        val wantedIds = wanted.mapTo(HashSet()) { it.id }

        syncOwnedIds().filterNot { it in wantedIds }.forEach { songId ->
            watchExportManager.removeExport(songId)
            setSyncOwned(songId, owned = false)
        }

        val exported = watchExportManager.exportedSongIds()
        wanted
            .filter { it.id !in exported }
            .filter { mayUseNetwork || watchExportManager.canExportOffline(it) }
            .forEach { song ->
                // Claimed before exporting: a newer sync may cancel this one while the export
                // itself still finishes, and the file must stay removable later.
                setSyncOwned(song.id, owned = true)
                watchExportManager.exportAndAwait(song).onFailure {
                    Timber.tag(TAG).w(it, "Watch sync could not export ${song.id}")
                    setSyncOwned(song.id, owned = false)
                }
            }
    }

    private fun syncOwnedIds(): Set<String> = preferences.getStringSet(OWNED_IDS_KEY, emptySet()).orEmpty().toSet()

    private fun setSyncOwned(songId: String, owned: Boolean) {
        synchronized(preferences) {
            val ids = syncOwnedIds()
            preferences.edit { putStringSet(OWNED_IDS_KEY, if (owned) ids + songId else ids - songId) }
        }
    }

    private fun unmeteredNetwork(): Flow<Boolean> =
        callbackFlow {
            val connectivityManager = context.getSystemService<ConnectivityManager>()
            if (connectivityManager == null) {
                trySend(false)
                awaitClose()
                return@callbackFlow
            }
            fun current() =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        trySend(current())
                    }

                    override fun onLost(network: Network) {
                        trySend(current())
                    }
                }
            trySend(current())
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()

    private companion object {
        const val TAG = "WatchPlaylistSync"
        const val PREFERENCES_NAME = "watch_playlist_sync"
        const val OWNED_IDS_KEY = "sync_owned_song_ids"
        const val SETTLE_DELAY_MS = 3_000L
    }
}
