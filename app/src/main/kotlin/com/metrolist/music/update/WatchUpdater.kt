package com.metrolist.music.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.metrolist.music.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** A build of Metrolist Watch published on GitHub. */
data class WatchRelease(
    val build: Int,
    val tag: String,
    val apkUrl: String,
    val size: Long,
    val notes: List<String>,
    val publishedAt: Instant?,
)

sealed interface UpdateState {
    data object UpToDate : UpdateState

    data class Available(val release: WatchRelease) : UpdateState

    data class Downloading(
        val release: WatchRelease,
        val bytes: Long,
        val total: Long,
        val bytesPerSecond: Long,
    ) : UpdateState {
        val fraction get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** Downloaded and checked, waiting for the system installer. */
    data class Ready(val release: WatchRelease, val file: File) : UpdateState

    data class Failed(val release: WatchRelease) : UpdateState
}

/**
 * Updates the app from this fork's GitHub releases: finds a newer build, downloads its APK with
 * progress and hands it to the system installer. The official Metrolist updater cannot do this,
 * as those builds are signed differently and lack the watch features.
 */
object WatchUpdater {
    private const val RELEASES_URL = "https://api.github.com/repos/MikeSPL187/Default/releases?per_page=10"
    private const val CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L
    private const val UPDATES_DIR = "updates"

    /** The build number CI stamps into the version name ("13.7.0-watch.3+b84"); null for local builds. */
    val currentBuild: Int? = buildNumber(BuildConfig.VERSION_NAME)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var download: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _releases = MutableStateFlow<List<WatchRelease>>(emptyList())
    val releases: StateFlow<List<WatchRelease>> = _releases.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _lastChecked = MutableStateFlow(0L)
    val lastChecked: StateFlow<Long> = _lastChecked.asStateFlow()

    internal fun buildNumber(versionName: String): Int? =
        Regex("""[+-]b(\d+)$""").find(versionName)?.groupValues?.get(1)?.toIntOrNull()

    /** The list items of a release body ("- …"), or its first line for older builds. */
    internal fun parseNotes(body: String): List<String> {
        val lines = body.lines().map { it.trim() }
        val items = lines.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }.filter { it.isNotEmpty() }
        return items.ifEmpty { listOfNotNull(lines.firstOrNull { it.isNotEmpty() }) }
    }

    internal fun parseReleases(json: String): List<WatchRelease> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val release = array.getJSONObject(i)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@mapNotNull null
            val tag = release.getString("tag_name")
            val build = buildNumber(tag) ?: return@mapNotNull null
            val assets = release.getJSONArray("assets")
            val apk =
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@mapNotNull null
            WatchRelease(
                build = build,
                tag = tag,
                apkUrl = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
                notes = parseNotes(release.optString("body").takeUnless { release.isNull("body") }.orEmpty()),
                publishedAt = runCatching { Instant.parse(release.getString("published_at")) }.getOrNull(),
            )
        }.sortedByDescending { it.build }
    }

    fun isNewer(release: WatchRelease) = currentBuild != null && release.build > currentBuild

    /**
     * Looks for a newer build, at most every two hours unless [force]d. Returns it, if any.
     * A download in progress or finished is left alone.
     */
    suspend fun checkForUpdate(force: Boolean = false): WatchRelease? =
        withContext(Dispatchers.IO) {
            val cached = _releases.value.firstOrNull()?.takeIf(::isNewer)
            if (currentBuild == null) return@withContext null
            if (!force && System.currentTimeMillis() - _lastChecked.value < CHECK_INTERVAL_MS) return@withContext cached
            _checking.value = true
            try {
                val releases = parseReleases(get(RELEASES_URL))
                _releases.value = releases
                _lastChecked.value = System.currentTimeMillis()
                val newest = releases.firstOrNull()?.takeIf(::isNewer)
                when (val current = _state.value) {
                    is UpdateState.Downloading, is UpdateState.Ready -> Unit
                    else ->
                        _state.value =
                            if (newest == null) {
                                UpdateState.UpToDate
                            } else if (current is UpdateState.Failed && current.release == newest) {
                                current
                            } else {
                                UpdateState.Available(newest)
                            }
                }
                newest
            } catch (e: Exception) {
                Timber.tag("Update").w(e, "Could not check for updates")
                cached
            } finally {
                _checking.value = false
            }
        }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        return try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads [release] in the background; the state follows its progress. */
    fun download(
        context: Context,
        release: WatchRelease,
    ) {
        if (download?.isActive == true) return
        val appContext = context.applicationContext
        download =
            scope.launch {
                val file = apkFile(appContext, release)
                try {
                    if (!file.exists()) fetch(release, file)
                    verify(appContext, release, file)
                    _state.value = UpdateState.Ready(release, file)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _state.value = UpdateState.Available(release)
                    throw e
                } catch (e: Exception) {
                    Timber.tag("Update").w(e, "Could not download build ${release.build}")
                    file.delete()
                    _state.value = UpdateState.Failed(release)
                }
            }
    }

    fun cancelDownload() {
        download?.cancel()
    }

    private suspend fun fetch(
        release: WatchRelease,
        file: File,
    ) {
        val part = File(file.parentFile, file.name + ".part").apply { delete() }
        _state.value = UpdateState.Downloading(release, 0, release.size, 0)
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            var bytes = 0L
            var speed = 0.0
            var mark = System.nanoTime()
            var markBytes = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytes += read
                        val now = System.nanoTime()
                        val elapsed = (now - mark) / 1e9
                        if (elapsed >= PROGRESS_STEP_S) {
                            val instant = (bytes - markBytes) / elapsed
                            speed = if (speed == 0.0) instant else speed * 0.7 + instant * 0.3
                            mark = now
                            markBytes = bytes
                            _state.value = UpdateState.Downloading(release, bytes, total, speed.toLong())
                        }
                    }
                }
            }
            check(total <= 0 || bytes == total) { "Incomplete download: $bytes of $total" }
            check(part.renameTo(file)) { "Could not keep the download" }
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    /** Refuses anything but a newer build of this very app, so a bad download never reaches the installer. */
    private fun verify(
        context: Context,
        release: WatchRelease,
        file: File,
    ) {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        check(info != null && info.packageName == context.packageName) { "Not an update of this app" }
        check(buildNumber(info.versionName.orEmpty()) == release.build) { "Unexpected build ${info.versionName}" }
    }

    private fun apkFile(
        context: Context,
        release: WatchRelease,
    ) = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }.resolve("MetrolistWatch-b${release.build}.apk")

    /** Whether Android lets this app open the installer; asked once, in system settings. */
    fun canInstall(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { Timber.tag("Update").w(it, "No installer") }
    }

    /** Deletes downloaded builds that are installed already. */
    fun cleanUp(context: Context) {
        val current = currentBuild ?: return
        File(context.cacheDir, UPDATES_DIR).listFiles()?.forEach { file ->
            val build = Regex("""-b(\d+)\.apk""").find(file.name)?.groupValues?.get(1)?.toIntOrNull()
            if (build == null || build <= current) file.delete()
        }
    }

    private const val TIMEOUT_MS = 20_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val PROGRESS_STEP_S = 0.25
}
