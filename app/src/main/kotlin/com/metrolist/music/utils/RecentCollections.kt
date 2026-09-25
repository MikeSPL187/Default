package com.metrolist.music.utils

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A playlist, album or artist the user opened, remembered for quick access on the home screen. */
@Serializable
data class RecentCollection(
    val kind: Kind,
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val openedAt: Long = System.currentTimeMillis(),
) {
    @Serializable
    enum class Kind { PLAYLIST, AUTO_PLAYLIST, ALBUM, ARTIST }

    val route: String
        get() =
            when (kind) {
                Kind.PLAYLIST -> "local_playlist/$id"
                Kind.AUTO_PLAYLIST -> "auto_playlist/$id"
                Kind.ALBUM -> "album/$id"
                Kind.ARTIST -> "artist/$id"
            }
}

/**
 * The last collections the user opened, newest first. Kept in preferences as JSON: a short list
 * that needs no table of its own.
 */
object RecentCollections {
    private val key = stringPreferencesKey("recentCollections")
    private val json = Json { ignoreUnknownKeys = true }
    private const val LIMIT = 12

    fun flow(context: Context): Flow<List<RecentCollection>> =
        context.dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun record(
        context: Context,
        item: RecentCollection,
    ) = context.safeDataStoreEdit { prefs ->
        val current = decode(prefs[key])
        prefs[key] = json.encodeToString((listOf(item) + current.filterNot { it.kind == item.kind && it.id == item.id }).take(LIMIT))
    }

    suspend fun remove(
        context: Context,
        item: RecentCollection,
    ) = context.safeDataStoreEdit { prefs ->
        prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.kind == item.kind && it.id == item.id })
    }

    private fun decode(raw: String?): List<RecentCollection> =
        raw?.let { runCatching { json.decodeFromString<List<RecentCollection>>(it) }.getOrNull() }.orEmpty()
}

/** Remembers [item] for quick access once the screen showing it has loaded it. */
@androidx.compose.runtime.Composable
fun RememberForQuickAccess(item: RecentCollection?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(item?.kind, item?.id) {
        item?.let { RecentCollections.record(context, it) }
    }
}
