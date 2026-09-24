/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.utils.read
import com.metrolist.music.utils.dataStore
import com.metrolist.music.constants.WatchStorageLimitMbKey
import androidx.core.content.edit
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.music.R
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.di.ApplicationScope
import com.metrolist.music.di.DownloadCache
import com.metrolist.music.utils.InnerTubeXPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface WatchExportState {
    data object NotExported : WatchExportState

    data object Queued : WatchExportState

    data class Exporting(val bytesCopied: Long, val totalBytes: Long?) : WatchExportState

    data class Exported(val displayName: String?) : WatchExportState

    data class Failed(val message: String) : WatchExportState
}

sealed interface WatchExportEvent {
    data class Success(val songTitle: String) : WatchExportEvent

    data class Failure(val songTitle: String, val message: String) : WatchExportEvent

    data class BatchFinished(val succeeded: Int, val failed: Int) : WatchExportEvent
}

data class WatchExportBatchState(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
) {
    val progress: Float
        get() = if (total == 0) 0f else completed.toFloat() / total
}

data class WatchExportedFile(
    val uri: Uri,
    val displayName: String,
    val usedOfflineDownload: Boolean,
    val sizeBytes: Long,
)

class WatchExportException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : IOException(message, cause)

/**
 * Copies downloaded songs as AAC (.m4a) files into Music/Metrolist Watch, where Huawei Health
 * picks them up for transfer to the watch. Downloads that are already AAC are copied straight
 * from the offline cache; Opus downloads are replaced by a freshly fetched AAC stream.
 */
@Singleton
class WatchExportManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @DownloadCache private val downloadCache: Cache,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private class ActiveExport(
        val deferred: Deferred<Result<WatchExportedFile>>,
        val notifyUser: AtomicBoolean,
    )

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val taskLock = Any()
    private val batchLock = Any()
    private val activeExports = HashMap<String, ActiveExport>()
    private var batchJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
    }

    private val _states =
        MutableStateFlow<Map<String, WatchExportState>>(
            exportedIds().associateWith { WatchExportState.Exported(null) },
        )
    val states: StateFlow<Map<String, WatchExportState>> = _states.asStateFlow()

    private val _exportedBytes = MutableStateFlow(computeExportedBytes())

    /** Space taken by exported files; files exported before sizes were recorded count as zero. */
    val exportedBytes: StateFlow<Long> = _exportedBytes.asStateFlow()

    private val _batchState = MutableStateFlow(WatchExportBatchState())
    val batchState: StateFlow<WatchExportBatchState> = _batchState.asStateFlow()

    private val _events = MutableSharedFlow<WatchExportEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<WatchExportEvent> = _events.asSharedFlow()

    fun state(songId: String): Flow<WatchExportState> =
        states.map { it[songId] ?: WatchExportState.NotExported }.distinctUntilChanged()

    /** Starts (or joins) an export of [song]; the result is reported through [events]. */
    fun export(song: Song) {
        taskFor(song, notifyUser = true)
    }

    /** Exports without a snackbar, e.g. automatically after a download finishes. */
    fun exportInBackground(song: Song) {
        taskFor(song, notifyUser = false)
    }

    fun exportAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        synchronized(batchLock) {
            if (batchJob?.isActive == true) return
            _batchState.value = WatchExportBatchState(running = true, total = songs.size)
            batchJob =
                applicationScope.launch(Dispatchers.IO) {
                    var succeeded = 0
                    var failed = 0
                    songs.forEachIndexed { index, song ->
                        val result = taskFor(song, notifyUser = false)?.deferred?.await()
                        if (result?.isSuccess == true) succeeded++ else failed++
                        _batchState.value =
                            WatchExportBatchState(true, songs.size, index + 1, succeeded, failed)
                    }
                    _batchState.value =
                        WatchExportBatchState(false, songs.size, songs.size, succeeded, failed)
                    _events.emit(WatchExportEvent.BatchFinished(succeeded, failed))
                }
        }
    }

    /** Exports [song] without notifying the user and waits for the result. */
    suspend fun exportAndAwait(song: Song): Result<WatchExportedFile> =
        taskFor(song, notifyUser = false)?.deferred?.await()
            ?: Result.failure(WatchExportException(context.getString(R.string.exporting_for_watch)))

    fun exportedSongIds(): Set<String> = exportedIds()

    /** Whether [song] can be exported from its offline download, without using the network. */
    fun canExportOffline(song: Song): Boolean {
        val format = song.format ?: return false
        return isWatchCompatible(format.mimeType, format.codecs) &&
            format.contentLength > 0 &&
            downloadCache.isCached(song.id, 0, format.contentLength)
    }

    /** Deletes the exported file of [songId] from the watch folder and forgets the export. */
    fun removeExport(songId: String) {
        val displayName = preferences.getString(DISPLAY_NAME_KEY_PREFIX + songId, null)
        if (displayName != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                        arrayOf(displayName, "${Environment.DIRECTORY_MUSIC}/$EXPORT_DIRECTORY/"),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val file = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), EXPORT_DIRECTORY), displayName)
                    if (file.delete()) {
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME_TYPE), null)
                    }
                }
            }.onFailure {
                // Files exported before a reinstall belong to the old install and cannot be deleted by us.
                Timber.tag(TAG).w(it, "Could not delete watch export $displayName")
            }
        }
        synchronized(preferences) {
            preferences.edit { remove(DISPLAY_NAME_KEY_PREFIX + songId) }
        }
        forgetExport(songId)
    }

    fun forgetExport(songId: String) {
        synchronized(preferences) {
            preferences.edit {
                putStringSet(EXPORTED_IDS_KEY, exportedIds() - songId)
                remove(SIZE_KEY_PREFIX + songId)
            }
        }
        _exportedBytes.value = computeExportedBytes()
        updateState(songId, WatchExportState.NotExported)
    }

    private val _limitReached = MutableStateFlow(false)

    /** True when an automatic export was skipped because the watch space limit is full. */
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    /** Whether an automatic export of [song] still fits in the watch space limit set by the user. */
    suspend fun fitsWatchLimit(song: Song): Boolean {
        val limitMb = context.dataStore.read(WatchStorageLimitMbKey, 0)
        val fits = limitMb <= 0 || exportedBytes.value + estimatedExportBytes(song) <= limitMb * 1024L * 1024L
        _limitReached.value = !fits
        return fits
    }

    /** Expected size of [song]'s export, used to stay under the watch space limit. */
    fun estimatedExportBytes(song: Song): Long {
        val format = song.format
        if (format != null && isWatchCompatible(format.mimeType, format.codecs) && format.contentLength > 0) {
            return format.contentLength
        }
        return song.song.duration.coerceAtLeast(0) * AAC_BYTES_PER_SECOND
    }

    private fun computeExportedBytes(): Long = exportedIds().sumOf { preferences.getLong(SIZE_KEY_PREFIX + it, 0L) }

    private fun taskFor(song: Song, notifyUser: Boolean): ActiveExport? =
        synchronized(taskLock) {
            val songId = song.id
            activeExports[songId]?.let { existing ->
                if (!existing.deferred.isActive) return@synchronized null
                if (notifyUser) existing.notifyUser.set(true)
                return@synchronized existing
            }

            val wasExported = _states.value[songId] is WatchExportState.Exported
            updateState(songId, WatchExportState.Queued)
            val notify = AtomicBoolean(notifyUser)
            val deferred =
                applicationScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    val result = exportTracked(song, wasExported)
                    if (notify.get()) {
                        _events.emit(
                            result.fold(
                                onSuccess = { WatchExportEvent.Success(song.song.title) },
                                onFailure = { WatchExportEvent.Failure(song.song.title, userFacingMessage(it)) },
                            ),
                        )
                    }
                    result
                }
            val active = ActiveExport(deferred, notify)
            activeExports[songId] = active
            deferred.invokeOnCompletion {
                synchronized(taskLock) {
                    if (activeExports[songId] === active) activeExports.remove(songId)
                }
            }
            deferred.start()
            active
        }

    private suspend fun exportTracked(song: Song, wasExported: Boolean): Result<WatchExportedFile> {
        val songId = song.id
        return try {
            updateState(songId, WatchExportState.Exporting(0L, null))
            val file = exportFromDownload(song) ?: exportFreshAac(song)
            markExported(songId)
            synchronized(preferences) {
                preferences.edit {
                    putString(DISPLAY_NAME_KEY_PREFIX + songId, file.displayName)
                    putLong(SIZE_KEY_PREFIX + songId, file.sizeBytes)
                }
            }
            _exportedBytes.value = computeExportedBytes()
            updateState(songId, WatchExportState.Exported(file.displayName))
            Result.success(file)
        } catch (error: CancellationException) {
            updateState(songId, if (wasExported) WatchExportState.Exported(null) else WatchExportState.NotExported)
            throw error
        } catch (error: Throwable) {
            Timber.tag(TAG).e(error, "Watch export failed for $songId")
            // A failed re-export must not hide the copy that is already on the phone.
            updateState(
                songId,
                if (wasExported) WatchExportState.Exported(null) else WatchExportState.Failed(userFacingMessage(error)),
            )
            Result.failure(error)
        }
    }

    private fun exportFromDownload(song: Song): WatchExportedFile? {
        val format = song.format ?: return null
        if (!isWatchCompatible(format.mimeType, format.codecs)) return null
        if (format.contentLength <= 0 || !downloadCache.isCached(song.id, 0, format.contentLength)) return null
        return publishAudio(song, usedOfflineDownload = true) { output ->
            openDownloadedStream(song.id, format).use { input ->
                copyWithProgress(song.id, input, output, 0L, format.contentLength)
            }
        }
    }

    private fun openDownloadedStream(songId: String, format: FormatEntity): InputStream {
        val dataSource = CacheDataSource(downloadCache, FileDataSource())
        val dataSpec =
            DataSpec.Builder()
                .setUri(songId.toUri())
                .setKey(songId)
                .setLength(format.contentLength)
                .build()
        return DataSourceInputStream(dataSource, dataSpec)
    }

    private suspend fun exportFreshAac(song: Song): WatchExportedFile {
        val file = downloadFreshAac(song)
        return try {
            publishAudio(song, usedOfflineDownload = false) { output ->
                FileInputStream(file).use { input ->
                    copyWithProgress(song.id, input, output, 0L, file.length())
                }
            }
        } finally {
            file.delete()
        }
    }

    /**
     * Downloads an AAC stream into a temporary file. Interrupted transfers are resumed with a
     * Range request; the stream is re-resolved on every attempt because signed URLs expire.
     */
    private suspend fun downloadFreshAac(song: Song): File {
        val directory = File(context.cacheDir, TEMP_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) throw storageError()
        val partial = File(directory, sanitizeWatchExportName(song.id, "track") + ".part")
        partial.delete()

        var lastError: Throwable? = null
        var expectedLength: Long? = null
        var lastItag: Int? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(minOf(RETRY_BASE_DELAY_MS shl (attempt - 1), RETRY_MAX_DELAY_MS))
            coroutineContext.ensureActive()
            try {
                val playbackData =
                    InnerTubeXPlayer.playerResponseForPlayback(
                        videoId = song.id,
                        audioQuality = AudioQuality.LOW,
                        connectivityManager = connectivityManager,
                        contentHints =
                            ContentHints(
                                isExplicit = song.song.explicit,
                                isUploaded = song.song.isUploaded,
                            ),
                    ).getOrElse { cause ->
                        throw WatchExportException(context.getString(R.string.watch_export_error_prepare), cause, retryable = true)
                    }
                val format = playbackData.format
                ensureWatchCompatible(format.mimeType)

                // A different itag or length means the resumable bytes belong to another file.
                if (lastItag != null && lastItag != format.itag) {
                    partial.delete()
                    expectedLength = null
                }
                val reportedLength = format.contentLength?.takeIf { it > 0 }
                if (expectedLength != null && reportedLength != null && expectedLength != reportedLength) {
                    partial.delete()
                    expectedLength = null
                }
                lastItag = format.itag
                expectedLength = expectedLength ?: reportedLength

                var offset = partial.length()
                if (expectedLength?.let { offset > it } == true) {
                    partial.delete()
                    offset = 0
                }
                if (expectedLength != null && offset == expectedLength && isPlayableM4a(partial)) {
                    return partial
                }

                val request =
                    Request.Builder()
                        .url(playbackData.streamUrl)
                        .apply { playbackData.streamHeaders.forEach { (name, value) -> header(name, value) } }
                        .header("Accept-Encoding", "identity")
                        .header("Range", "bytes=$offset-")
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 416 && expectedLength == partial.length() && isPlayableM4a(partial)) {
                        return partial
                    }
                    if (response.code != 200 && response.code != 206) throw IOException("HTTP ${response.code}")

                    val append = response.code == 206 && offset > 0
                    if (!append && offset > 0) {
                        partial.delete()
                        offset = 0
                    }
                    val bodyLength = response.body.contentLength().takeIf { it > 0 }
                    val rangeTotal =
                        response.header("Content-Range")
                            ?.substringAfter('/', "")
                            ?.takeUnless { it.isBlank() || it == "*" }
                            ?.toLongOrNull()
                    expectedLength =
                        rangeTotal
                            ?: bodyLength?.let { if (response.code == 206) it + offset else it }
                            ?: expectedLength

                    FileOutputStream(partial, append).use { output ->
                        response.body.byteStream().use { input ->
                            copyWithProgress(song.id, input, output, offset, expectedLength)
                        }
                    }
                }

                val downloaded = partial.length()
                expectedLength?.let { expected ->
                    if (downloaded != expected) throw IOException("Incomplete response: $downloaded of $expected bytes")
                }
                if (isPlayableM4a(partial)) return partial
                partial.delete()
                expectedLength = null
                throw IOException("Downloaded AAC container is incomplete")
            } catch (error: CancellationException) {
                partial.delete()
                throw error
            } catch (error: Throwable) {
                if (error is WatchExportException && !error.retryable) {
                    partial.delete()
                    throw error
                }
                lastError = error
                Timber.tag(TAG).w(error, "Watch export attempt ${attempt + 1}/$MAX_ATTEMPTS failed for ${song.id}")
            }
        }

        partial.delete()
        throw WatchExportException(context.getString(R.string.watch_export_error_incomplete), lastError)
    }

    private fun publishAudio(
        song: Song,
        usedOfflineDownload: Boolean,
        writer: (OutputStream) -> Unit,
    ): WatchExportedFile {
        val artists = song.orderedArtists.joinToString(", ") { it.name }
        val displayName =
            sanitizeWatchExportName(
                "${artists.ifBlank { "Unknown artist" }} - ${song.song.title}",
                song.id,
            ) + ".m4a"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(song, artists, displayName, usedOfflineDownload, writer)
        } else {
            publishToLegacyStorage(displayName, usedOfflineDownload, writer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(
        song: Song,
        artists: String,
        displayName: String,
        usedOfflineDownload: Boolean,
        writer: (OutputStream) -> Unit,
    ): WatchExportedFile {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_MUSIC}/$EXPORT_DIRECTORY/"

        val previousCopies =
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(displayName, relativePath),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                buildList {
                    while (cursor.moveToNext()) add(Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString()))
                }
            }.orEmpty()

        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.TITLE, song.song.title)
                put(MediaStore.Audio.AudioColumns.ARTIST, artists)
                song.album?.title?.let { put(MediaStore.Audio.AudioColumns.ALBUM, it) }
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.AudioColumns.IS_MUSIC, 1)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri = resolver.insert(collection, values) ?: throw storageError()
        var writtenBytes = 0L
        try {
            val output = resolver.openOutputStream(uri, "w") ?: throw storageError()
            CountingOutputStream(output).use {
                writer(it)
                writtenBytes = it.count
            }
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, published, null, null) <= 0) throw storageError()
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        // Only drop the old copy once the replacement is fully written.
        previousCopies.filter { it != uri }.forEach { resolver.delete(it, null, null) }
        return WatchExportedFile(uri, displayName, usedOfflineDownload, writtenBytes)
    }

    private fun publishToLegacyStorage(
        displayName: String,
        usedOfflineDownload: Boolean,
        writer: (OutputStream) -> Unit,
    ): WatchExportedFile {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw WatchExportException(context.getString(R.string.watch_export_error_permission))
        }
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), EXPORT_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) throw storageError()

        val target = File(directory, displayName)
        val pending = File(directory, ".$displayName.pending")
        try {
            FileOutputStream(pending, false).use(writer)
            val writtenBytes = pending.length()
            if (target.exists() && !target.delete()) throw storageError()
            if (!pending.renameTo(target)) throw storageError()
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME_TYPE), null)
            return WatchExportedFile(Uri.fromFile(target), displayName, usedOfflineDownload, writtenBytes)
        } finally {
            pending.delete()
        }
    }

    private fun copyWithProgress(
        songId: String,
        input: InputStream,
        output: OutputStream,
        startOffset: Long,
        totalBytes: Long?,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = startOffset
        var lastReported = startOffset
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (copied - lastReported >= PROGRESS_STEP_BYTES) {
                lastReported = copied
                updateState(songId, WatchExportState.Exporting(copied, totalBytes))
            }
        }
        output.flush()
        updateState(songId, WatchExportState.Exporting(copied, totalBytes))
    }

    private fun ensureWatchCompatible(mimeType: String) {
        val container = mimeType.substringBefore(';').trim()
        val codecs =
            mimeType.substringAfter("codecs=", "")
                .substringBefore(';')
                .trim()
                .removeSurrounding("\"")
        if (!isWatchCompatible(container, codecs)) {
            throw WatchExportException(context.getString(R.string.watch_export_error_format))
        }
    }

    private fun markExported(songId: String) {
        synchronized(preferences) {
            if (!preferences.edit().putStringSet(EXPORTED_IDS_KEY, exportedIds() + songId).commit()) {
                Timber.tag(TAG).w("Could not persist watch export status for $songId")
            }
        }
    }

    private fun exportedIds(): Set<String> = preferences.getStringSet(EXPORTED_IDS_KEY, emptySet()).orEmpty().toSet()

    private fun updateState(songId: String, state: WatchExportState) {
        _states.update { it + (songId to state) }
    }

    private fun storageError() = WatchExportException(context.getString(R.string.watch_export_error_storage))

    private fun userFacingMessage(error: Throwable): String {
        var current: Throwable? = error
        while (current != null) {
            if (current is WatchExportException && !current.message.isNullOrBlank()) return current.message!!
            current = current.cause
        }
        return context.getString(R.string.watch_export_error_incomplete)
    }

    private companion object {
        const val TAG = "WatchExport"
        const val PREFERENCES_NAME = "watch_export_status"
        const val EXPORTED_IDS_KEY = "exported_song_ids"
        const val DISPLAY_NAME_KEY_PREFIX = "display_name_"
        const val SIZE_KEY_PREFIX = "size_"

        /** 128 kbit/s, the usual YouTube AAC stream. */
        const val AAC_BYTES_PER_SECOND = 16_000L
        const val EXPORT_DIRECTORY = "Metrolist Watch"
        const val TEMP_DIRECTORY = "watch_exports"
        const val MIME_TYPE = "audio/mp4"
        const val MAX_ATTEMPTS = 5
        const val RETRY_BASE_DELAY_MS = 750L
        const val RETRY_MAX_DELAY_MS = 6_000L
        const val PROGRESS_STEP_BYTES = 512 * 1024L
    }
}

internal fun isWatchCompatible(mimeType: String, codecs: String): Boolean =
    mimeType.trim().equals("audio/mp4", ignoreCase = true) &&
        (codecs.isBlank() || codecs.trim().startsWith("mp4a", ignoreCase = true))

private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
private val WHITESPACE = Regex("""\s+""")

internal fun sanitizeWatchExportName(name: String, fallback: String): String {
    val cleaned =
        name.replace(INVALID_FILE_NAME_CHARS, "_")
            .replace(WHITESPACE, " ")
            .trim(' ', '.')
    return cleaned.ifBlank { fallback }.take(180).trimEnd(' ', '.')
}

private fun isPlayableM4a(file: File): Boolean {
    if (!file.isFile || file.length() < 16) return false
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(file.absolutePath)
        (0 until extractor.trackCount).any { index ->
            extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
    } catch (_: Throwable) {
        false
    } finally {
        extractor.release()
    }
}

private class CountingOutputStream(
    private val target: OutputStream,
) : java.io.FilterOutputStream(target) {
    var count = 0L
        private set

    override fun write(b: Int) {
        target.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        target.write(b, off, len)
        count += len
    }
}
