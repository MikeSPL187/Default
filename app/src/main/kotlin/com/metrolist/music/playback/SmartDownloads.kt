package com.metrolist.music.playback

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.constants.SmartDownloadsCountKey
import com.metrolist.music.constants.SmartDownloadsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.read
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the user's most played songs and current quick picks downloaded. A daily job refreshes the
 * set on an unmetered network while charging. Only downloads this feature added are ever removed,
 * and never liked songs.
 */
@Singleton
class SmartDownloads
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun schedule(enabled: Boolean) {
        val scheduler = context.getSystemService<JobScheduler>() ?: return
        if (!enabled) {
            scheduler.cancel(JOB_ID)
            return
        }
        val job =
            JobInfo
                .Builder(JOB_ID, ComponentName(context, SmartDownloadsJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setPersisted(true)
                .build()
        runCatching { scheduler.schedule(job) }.onFailure { Timber.tag(TAG).w(it, "Could not schedule smart downloads") }
    }

    /**
     * Downloads what is missing from the smart set and removes what fell out of it.
     * @param viaService false when running in the background job, see [DownloadUtil.download].
     */
    suspend fun refresh(viaService: Boolean) = withContext(Dispatchers.IO) {
        val enabled = context.dataStore.read(SmartDownloadsKey, false)
        val owned = ownedIds()
        val wanted = if (enabled) wantedSongs(context.dataStore.read(SmartDownloadsCountKey, DEFAULT_COUNT)) else emptyList()
        val downloads = downloadUtil.downloads.value
        val plan =
            planSmartDownloads(
                wantedIds = wanted.map { it.id },
                ownedIds = owned,
                downloadStates = downloads.mapValues { it.value.state },
                isLiked = { database.getSongByIdBlocking(it)?.song?.liked == true },
            )

        plan.release.forEach { songId ->
            if (songId in plan.toRemove) {
                if (viaService) {
                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
                } else {
                    withContext(Dispatchers.Main) { downloadUtil.downloadManager.removeDownload(songId) }
                }
            }
            setOwned(songId, owned = false)
        }
        if (!enabled) return@withContext

        val toDownload = plan.toDownload.toHashSet()
        val preparations =
            wanted
                .filter { it.id in toDownload }
                .map { song ->
                    if (song.id in plan.toClaim) setOwned(song.id, owned = true)
                    downloadUtil.download(song.toMediaMetadata(), viaService)
                }
        // The background job must not finish before the requests reach the download manager.
        if (!viaService) preparations.joinAll()
    }

    /**
     * Waits until none of this feature's downloads are still queued or running. The download list is
     * updated asynchronously after a request is added, so it is only trusted after [SETTLE_MS].
     */
    suspend fun awaitDownloads() {
        val start = System.currentTimeMillis()
        while (true) {
            val downloads = downloadUtil.downloads.value
            val active =
                ownedIds().any { id ->
                    downloads[id]?.state.let { it == Download.STATE_QUEUED || it == Download.STATE_DOWNLOADING || it == Download.STATE_RESTARTING }
                }
            if (!active && System.currentTimeMillis() - start >= SETTLE_MS) return
            delay(POLL_MS)
        }
    }

    private suspend fun wantedSongs(count: Int): List<Song> {
        val mostPlayed = database.mostPlayedSongs(LocalDateTime.now().minusDays(MOST_PLAYED_DAYS), limit = count).first()
        val quickPicks = database.quickPicks().first().take(QUICK_PICKS_COUNT)
        return (mostPlayed + quickPicks)
            .filterNot { it.song.isEpisode || it.song.isLocal }
            .distinctBy { it.id }
    }

    private fun ownedIds(): Set<String> = preferences.getStringSet(OWNED_IDS_KEY, emptySet()).orEmpty().toSet()

    private fun setOwned(songId: String, owned: Boolean) {
        synchronized(preferences) {
            val ids = ownedIds()
            preferences.edit { putStringSet(OWNED_IDS_KEY, if (owned) ids + songId else ids - songId) }
        }
    }

    companion object {
        const val DEFAULT_COUNT = 50
        val COUNT_OPTIONS = listOf(25, 50, 100, 200)
        private const val TAG = "SmartDownloads"
        private const val JOB_ID = 0x5D0A
        private const val PREFERENCES_NAME = "smart_downloads"
        private const val OWNED_IDS_KEY = "owned_song_ids"
        private const val MOST_PLAYED_DAYS = 90L
        private const val QUICK_PICKS_COUNT = 15
        private const val SETTLE_MS = 15_000L
        private const val POLL_MS = 5_000L
    }
}

internal data class SmartDownloadPlan(
    /** Owned songs that left the smart set; they stop being owned. */
    val release: Set<String>,
    /** Of [release], the downloads to delete: liked songs keep theirs. */
    val toRemove: Set<String>,
    val toDownload: List<String>,
    /** Of [toDownload], the songs this feature starts owning. */
    val toClaim: Set<String>,
)

/**
 * Decides what smart downloads changes. Downloads the user made themselves are never claimed, so
 * they are never removed, and a liked song keeps its download when it leaves the set.
 */
internal fun planSmartDownloads(
    wantedIds: List<String>,
    ownedIds: Set<String>,
    downloadStates: Map<String, Int>,
    isLiked: (String) -> Boolean,
): SmartDownloadPlan {
    val wanted = wantedIds.distinct()
    val release = ownedIds - wanted.toSet()
    val toDownload = wanted.filter { downloadStates[it] != Download.STATE_COMPLETED }
    return SmartDownloadPlan(
        release = release,
        toRemove = release.filterNot(isLiked).toSet(),
        toDownload = toDownload,
        toClaim = toDownload.filter { it in ownedIds || downloadStates[it] == null }.toSet(),
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmartDownloadsEntryPoint {
    fun smartDownloads(): SmartDownloads
}

fun Context.smartDownloads(): SmartDownloads =
    EntryPointAccessors.fromApplication(applicationContext, SmartDownloadsEntryPoint::class.java).smartDownloads()

@AndroidEntryPoint
class SmartDownloadsJobService : JobService() {
    @Inject
    lateinit var smartDownloads: SmartDownloads

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work =
            scope.launch {
                try {
                    smartDownloads.refresh(viaService = false)
                    // Keep the job, and with it the process, alive while the downloads run.
                    withTimeoutOrNull(MAX_RUN_MS) { smartDownloads.awaitDownloads() }
                } catch (e: CancellationException) {
                    // Stopped by the system: onStopJob already answered, so jobFinished must not be called.
                    throw e
                } catch (e: Exception) {
                    Timber.tag("SmartDownloads").w(e, "Smart downloads refresh failed")
                }
                jobFinished(params, false)
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        // Unfinished downloads stay queued in the download manager and resume later.
        return false
    }

    private companion object {
        const val MAX_RUN_MS = 9 * 60 * 1000L
    }
}
