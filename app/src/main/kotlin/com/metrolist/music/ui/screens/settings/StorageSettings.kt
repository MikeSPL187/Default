/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.runtime.mutableFloatStateOf
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.playback.smartDownloads
import com.metrolist.music.playback.SmartDownloads
import com.metrolist.music.constants.SmartDownloadsCountKey
import com.metrolist.music.constants.SmartDownloadsKey
import com.metrolist.music.ui.utils.frameAwareDelay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AutoExportForWatchKey
import com.metrolist.music.constants.DownloadOnWifiOnlyKey
import com.metrolist.music.constants.EnableSongCacheKey
import com.metrolist.music.constants.MaxImageCacheSizeKey
import com.metrolist.music.constants.MaxSongCacheSizeKey
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import android.text.format.Formatter
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.rememberSharedStorageAction
import com.metrolist.music.utils.OfflineArtworkStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class, DelicateCoilApi::class)
@Composable
fun StorageSettings(
    navController: NavController
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return

    val coroutineScope = rememberCoroutineScope()
    val songCacheString = stringResource(R.string.song_cache).lowercase()
    val imageCacheString = stringResource(R.string.image_cache).lowercase()
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
        key = MaxSongCacheSizeKey,
        defaultValue = 1024
    )
    val (enableSongCache, onEnableSongCacheChange) = rememberPreference(
        key = EnableSongCacheKey,
        defaultValue = true
    )

    var clearDownloads by remember { mutableStateOf(false) }
    var exportAllForWatchDialog by remember { mutableStateOf(false) }
    val downloadUtil = LocalDownloadUtil.current
    val watchSyncedPlaylistIds by downloadUtil.watchPlaylistSync.syncedPlaylistIds
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val watchExportBatch by downloadUtil.watchExportManager.batchState.collectAsStateWithLifecycle()
    val requestExportAllForWatch = rememberSharedStorageAction { exportAllForWatchDialog = true }
    val (autoExportForWatch, onAutoExportForWatchChange) = rememberPreference(
        key = AutoExportForWatchKey,
        defaultValue = false
    )
    val enableAutoExportForWatch = rememberSharedStorageAction { onAutoExportForWatchChange(true) }
    val (downloadOnWifiOnly, onDownloadOnWifiOnlyChange) = rememberPreference(
        key = DownloadOnWifiOnlyKey,
        defaultValue = false
    )
    val smartDownloadsEnabled by rememberPreference(SmartDownloadsKey, defaultValue = false)
    val smartDownloadsCount by rememberPreference(SmartDownloadsCountKey, defaultValue = SmartDownloads.DEFAULT_COUNT)
    // Slider position as an index into SmartDownloads.COUNT_OPTIONS, committed when the drag ends.
    var smartDownloadsCountIndex by remember(smartDownloadsCount) {
        mutableFloatStateOf(
            (
                SmartDownloads.COUNT_OPTIONS.indexOf(smartDownloadsCount)
                    .takeIf { it >= 0 }
                    ?: SmartDownloads.COUNT_OPTIONS.indexOf(SmartDownloads.DEFAULT_COUNT)
            ).toFloat(),
        )
    }
    // The preference is written before refreshing, so the refresh sees the new value.
    val updateSmartDownloads: (enabled: Boolean, count: Int) -> Unit = { enabled, count ->
        coroutineScope.launch {
            context.safeDataStoreEdit {
                it[SmartDownloadsKey] = enabled
                it[SmartDownloadsCountKey] = count
            }
            val smartDownloads = context.smartDownloads()
            smartDownloads.schedule(enabled)
            smartDownloads.refresh(viaService = true)
        }
    }
    var clearCacheDialog by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }

    // State for the confirmation dialog
    var showCacheWarningDialog by remember { mutableStateOf(false) }
    var cacheType by remember { mutableStateOf("") }
    var cacheUsage by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var onConfirmAction by remember { mutableStateOf<() -> Unit>({}) }

    var imageCacheSize by remember {
        androidx.compose.runtime.mutableLongStateOf(imageDiskCache.size)
    }
    var playerCacheSize by remember {
        androidx.compose.runtime.mutableLongStateOf(tryOrNull { playerCache.cacheSpace } ?: 0)
    }
    var downloadCacheSize by remember {
        mutableLongStateOf(tryOrNull { downloadCache.cacheSpace } ?: 0)
    }
    val imageCacheProgress by animateFloatAsState(
        targetValue =
            (imageCacheSize.toFloat() / (maxImageCacheSize * 1024 * 1024L)).coerceIn(
                0f,
                1f,
            ),
        label = "imageCacheProgress",
    )
    val playerCacheProgress by animateFloatAsState(
        targetValue =
            (playerCacheSize.toFloat() / (maxSongCacheSize * 1024 * 1024L)).coerceIn(
                0f,
                1f,
            ),
        label = "playerCacheProgress",
    )

    LaunchedEffect(maxImageCacheSize) {
        SingletonImageLoader.reset()
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache.clear()
            }
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                playerCache.keys.forEach { key ->
                    playerCache.removeResource(key)
                }
            }
        }
    }

    LaunchedEffect(imageDiskCache) {
        while (isActive) {
            frameAwareDelay(CACHE_SIZE_REFRESH_MS)
            imageCacheSize = imageDiskCache.size
        }
    }
    LaunchedEffect(playerCache) {
        while (isActive) {
            frameAwareDelay(CACHE_SIZE_REFRESH_MS)
            playerCacheSize = tryOrNull { playerCache.cacheSpace } ?: 0
        }
    }
    LaunchedEffect(downloadCache) {
        while (isActive) {
            frameAwareDelay(CACHE_SIZE_REFRESH_MS)
            downloadCacheSize = tryOrNull { downloadCache.cacheSpace } ?: 0
        }
    }

    if (clearDownloads) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_all_downloads),
            onDismiss = { clearDownloads = false },
            onConfirm = {
                // Going through the download service also clears the download index and the
                // "downloaded" flags; removing cache keys alone left songs marked as offline.
                DownloadService.sendRemoveAllDownloads(context, ExoDownloadService::class.java, false)
                coroutineScope.launch(Dispatchers.IO) {
                    downloadCache.keys.forEach { key ->
                        downloadCache.removeResource(key)
                    }
                    OfflineArtworkStore.clear()
                }
                clearDownloads = false
            },
            onCancel = { clearDownloads = false },
            content = {
                Text(text = stringResource(R.string.clear_downloads_dialog))
            },
        )
    }
    if (exportAllForWatchDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.export_all_for_watch),
            onDismiss = { exportAllForWatchDialog = false },
            onConfirm = {
                exportAllForWatchDialog = false
                coroutineScope.launch {
                    val songs = withContext(Dispatchers.IO) { database.downloadedSongsByNameAsc().first() }
                    if (songs.isEmpty()) {
                        Toast.makeText(context, R.string.no_downloads_to_export, Toast.LENGTH_SHORT).show()
                    } else {
                        downloadUtil.watchExportManager.exportAll(songs)
                    }
                }
            },
            onCancel = { exportAllForWatchDialog = false },
            content = {
                Text(text = stringResource(R.string.export_all_for_watch_confirm))
            },
        )
    }
    if (clearCacheDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_song_cache),
            onDismiss = { clearCacheDialog = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    playerCache.keys.forEach { key ->
                        playerCache.removeResource(key)
                    }
                }
                clearCacheDialog = false
            },
            onCancel = { clearCacheDialog = false },
            content = {
                Text(text = stringResource(R.string.clear_song_cache_dialog))
            },
        )
    }
    if (clearImageCacheDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_image_cache),
            onDismiss = { clearImageCacheDialog = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    val urlsToPreserve = mutableSetOf<String>()
                    val downloadedSongs =
                        try {
                            database.downloadedSongsByNameAsc().first()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    downloadedSongs.forEach { song ->
                        song.song.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                        song.album?.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                    }
                    val directory = imageDiskCache.directory.toFile()
                    if (directory.exists() && directory.isDirectory) {
                        directory.listFiles()?.forEach { file ->
                            if (file.isFile && !file.name.startsWith("journal")) {
                                val isPreserved = urlsToPreserve.any { hash -> file.name.startsWith(hash) }
                                if (!isPreserved) {
                                    file.delete()
                                }
                            }
                        }
                    }
                    imageDiskCache.clear()
                }
                clearImageCacheDialog = false
            },
            onCancel = { clearImageCacheDialog = false },
            content = {
                Text(text = stringResource(R.string.clear_image_cache_dialog))
            },
        )
    }

    // Confirmation Dialog
    if (showCacheWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCacheWarningDialog = false },
            title = { Text(stringResource(R.string.cache_size_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cache_size_warning_message,
                        Formatter.formatShortFileSize(context, cacheUsage),
                        cacheType,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmAction()
                        showCacheWarningDialog = false
                    },
                ) {
                    Text(
                        stringResource(R.string.cache_size_warning_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheWarningDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )
        Material3SettingsGroup(
            title = stringResource(R.string.storage),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = { Text(stringResource(R.string.downloaded_songs)) },
                        description = {
                            Text(text = Formatter.formatShortFileSize(context, downloadCacheSize))
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.wifi),
                        title = { Text(stringResource(R.string.download_on_wifi_only)) },
                        description = { Text(stringResource(R.string.download_on_wifi_only_desc)) },
                        trailingContent = {
                            Switch(
                                checked = downloadOnWifiOnly,
                                onCheckedChange = onDownloadOnWifiOnlyChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (downloadOnWifiOnly) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onDownloadOnWifiOnlyChange(!downloadOnWifiOnly) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.trending_up),
                        title = { Text(stringResource(R.string.smart_downloads)) },
                        description = { Text(stringResource(R.string.smart_downloads_desc)) },
                        trailingContent = {
                            Switch(
                                checked = smartDownloadsEnabled,
                                onCheckedChange = { updateSmartDownloads(it, smartDownloadsCount) },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (smartDownloadsEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { updateSmartDownloads(!smartDownloadsEnabled, smartDownloadsCount) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.trending_up),
                        title = {
                            Text(
                                stringResource(
                                    R.string.smart_downloads_count,
                                    SmartDownloads.COUNT_OPTIONS[smartDownloadsCountIndex.roundToInt()],
                                ),
                            )
                        },
                        enabled = smartDownloadsEnabled,
                        description = {
                            Slider(
                                value = smartDownloadsCountIndex,
                                enabled = smartDownloadsEnabled,
                                valueRange = 0f..SmartDownloads.COUNT_OPTIONS.lastIndex.toFloat(),
                                steps = SmartDownloads.COUNT_OPTIONS.size - 2,
                                onValueChange = { smartDownloadsCountIndex = it },
                                onValueChangeFinished = {
                                    val count = SmartDownloads.COUNT_OPTIONS[smartDownloadsCountIndex.roundToInt()]
                                    if (count != smartDownloadsCount) updateSmartDownloads(true, count)
                                },
                            )
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.watch_check),
                        title = { Text(stringResource(R.string.export_all_for_watch)) },
                        description = {
                            if (watchExportBatch.running) {
                                Column {
                                    Text(
                                        stringResource(
                                            R.string.export_for_watch_progress,
                                            watchExportBatch.completed,
                                            watchExportBatch.total,
                                        ),
                                    )
                                    LinearProgressIndicator(
                                        progress = { watchExportBatch.progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        strokeCap = StrokeCap.Round,
                                    )
                                }
                            } else {
                                Text(stringResource(R.string.export_all_for_watch_desc))
                            }
                        },
                        onClick = {
                            if (!watchExportBatch.running) requestExportAllForWatch()
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.watch_check),
                        title = { Text(stringResource(R.string.auto_export_for_watch)) },
                        description = { Text(stringResource(R.string.auto_export_for_watch_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoExportForWatch,
                                onCheckedChange = { if (it) enableAutoExportForWatch() else onAutoExportForWatchChange(false) },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (autoExportForWatch) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = {
                            if (autoExportForWatch) onAutoExportForWatchChange(false) else enableAutoExportForWatch()
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text(stringResource(R.string.watch_synced_playlists)) },
                        description = {
                            Text(stringResource(R.string.watch_synced_playlists_desc, watchSyncedPlaylistIds.size))
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.clear_all_downloads)) },
                        onClick = {
                            clearDownloads = true
                        },
                    ),
                ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.song_cache),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.cached),
                    title = { Text(stringResource(R.string.enable_song_cache)) },
                    description = { Text(stringResource(R.string.enable_song_cache_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableSongCache,
                            onCheckedChange = onEnableSongCacheChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableSongCache) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableSongCacheChange(!enableSongCache) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.cached),
                    title = { Text(stringResource(R.string.max_song_cache_size)) },
                    enabled = enableSongCache,
                    description = {
                        val songCacheValues =
                            remember { listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1) }
                        Column {
                            Text(
                                text = when (maxSongCacheSize) {
                                    0 -> stringResource(R.string.disable)
                                    -1 -> stringResource(R.string.unlimited)
                                    else -> Formatter.formatShortFileSize(context, maxSongCacheSize * 1024 * 1024L)
                                }
                            )
                            Slider(
                                value = songCacheValues.indexOf(maxSongCacheSize).toFloat(),
                                enabled = enableSongCache,
                                onValueChange = {
                                    val newValue = songCacheValues[it.roundToInt()]
                                    val newLimitInBytes = if (newValue == -1) {
                                        Long.MAX_VALUE
                                    } else {
                                        newValue * 1024 * 1024L
                                    }

                                        if (newLimitInBytes < playerCacheSize) {
                                            cacheUsage = playerCacheSize
                                            cacheType = songCacheString
                                            onConfirmAction = { onMaxSongCacheSizeChange(newValue) }
                                            showCacheWarningDialog = true
                                        } else {
                                            onMaxSongCacheSizeChange(newValue)
                                        }
                                    },
                                    steps = songCacheValues.size - 2,
                                    valueRange = 0f..(songCacheValues.size - 1).toFloat(),
                                )
                                LinearProgressIndicator(
                                    progress = { playerCacheProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text(
                                    text =
                                        if (maxSongCacheSize == -1) {
                                            Formatter.formatShortFileSize(context, playerCacheSize)
                                        } else {
                                            "${Formatter.formatShortFileSize(context, playerCacheSize)} / ${
                                                Formatter.formatShortFileSize(context, 
                                                    maxSongCacheSize * 1024 * 1024L,
                                                )
                                            }"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.clear_song_cache)) },
                        onClick = {
                            clearCacheDialog = true
                        },
                    ),
                ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.image_cache),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.manage_search),
                        title = { Text(stringResource(R.string.max_image_cache_size)) },
                        description = {
                            val imageCacheValues =
                                remember { listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192) }
                            Column {
                                Text(
                                    text =
                                        when (maxImageCacheSize) {
                                            0 -> stringResource(R.string.disable)
                                            else -> Formatter.formatShortFileSize(context, maxImageCacheSize * 1024 * 1024L)
                                        },
                                )
                                Slider(
                                    value = imageCacheValues.indexOf(maxImageCacheSize).toFloat(),
                                    onValueChange = {
                                        val newValue = imageCacheValues[it.roundToInt()]
                                        val newLimitInBytes = newValue * 1024 * 1024L

                                        if (newLimitInBytes < imageCacheSize) {
                                            cacheUsage = imageCacheSize
                                            cacheType = imageCacheString
                                            onConfirmAction = { onMaxImageCacheSizeChange(newValue) }
                                            showCacheWarningDialog = true
                                        } else {
                                            onMaxImageCacheSizeChange(newValue)
                                        }
                                    },
                                    steps = imageCacheValues.size - 2,
                                    valueRange = 0f..(imageCacheValues.size - 1).toFloat(),
                                )
                                LinearProgressIndicator(
                                    progress = { imageCacheProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text(
                                    text = "${Formatter.formatShortFileSize(context, imageCacheSize)} / ${
                                        Formatter.formatShortFileSize(context, 
                                            maxImageCacheSize * 1024 * 1024L,
                                        )
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.clear_image_cache)) },
                        onClick = {
                            clearImageCacheDialog = true
                        },
                    ),
                ),
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.storage)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

// Cache sizes are read under the cache locks shared with playback; once a second is plenty.
private const val CACHE_SIZE_REFRESH_MS = 1_000L
