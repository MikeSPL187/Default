/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import coil3.request.ImageResult
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private val GOOGLEUSERCONTENT_SIZED =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=(?:w\\d+-h\\d+|s\\d+)[^?]*(\\?.*)?$")
private val GGPHT_SIZED = Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")

/**
 * Identifies artwork regardless of the size the UI asks for, so a cover saved once matches every
 * resized request for it.
 */
internal fun artworkKey(url: String): String =
    GOOGLEUSERCONTENT_SIZED.matchEntire(url)?.groupValues?.get(1)
        ?: GGPHT_SIZED.matchEntire(url)?.groupValues?.get(1)
        ?: url.substringBefore('?')

/**
 * Keeps the covers of downloaded songs in app storage. Coil's disk cache is an LRU cache that
 * evicts them, which left offline songs without artwork and made covers re-download over data.
 */
object OfflineArtworkStore {
    private const val TAG = "OfflineArtwork"
    private const val DIRECTORY = "offline_artwork"

    @Volatile
    private var directory: File? = null
    private val storedKeys = ConcurrentHashMap.newKeySet<String>()

    fun initialize(context: Context) {
        if (directory != null) return
        val dir = context.filesDir.resolve(DIRECTORY).apply { mkdirs() }
        directory = dir
        dir.listFiles()?.forEach { storedKeys += it.name }
    }

    private fun fileName(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The stored cover for [url], if any. Cheap enough to call for every image request. */
    fun fileFor(url: String): File? {
        val dir = directory ?: return null
        val name = fileName(artworkKey(url))
        return if (name in storedKeys) dir.resolve(name).takeIf(File::isFile) else null
    }

    fun contains(url: String): Boolean = fileFor(url) != null

    /** Stores [bytes] as the cover for [url]; written atomically so readers never see a partial file. */
    fun save(url: String, bytes: ByteArray) {
        val dir = directory ?: return
        if (bytes.isEmpty()) return
        val name = fileName(artworkKey(url))
        val temp = dir.resolve("$name.tmp")
        runCatching {
            temp.writeBytes(bytes)
            if (!temp.renameTo(dir.resolve(name))) error("rename failed")
            storedKeys += name
        }.onFailure {
            temp.delete()
            Timber.tag(TAG).w(it, "Could not store artwork")
        }
    }

    /** Deletes covers that no longer belong to any downloaded song. */
    fun retainOnly(urls: Collection<String>) {
        val dir = directory ?: return
        val keep = urls.mapTo(HashSet()) { fileName(artworkKey(it)) }
        dir.listFiles()?.forEach { file ->
            if (file.name !in keep) {
                file.delete()
                storedKeys -= file.name
            }
        }
    }

    fun clear() = retainOnly(emptyList())
}

/** Serves stored covers from disk before Coil touches its caches or the network. */
object OfflineArtworkInterceptor : coil3.intercept.Interceptor {
    override suspend fun intercept(chain: coil3.intercept.Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String
        val file = url?.takeIf { it.startsWith("http") }?.let(OfflineArtworkStore::fileFor) ?: return chain.proceed()
        return chain.withRequest(chain.request.newBuilder().data(file).build()).proceed()
    }
}
