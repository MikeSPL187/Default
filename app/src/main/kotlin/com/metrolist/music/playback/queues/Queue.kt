/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) removeWhere { it.metadata?.explicit == true } else this

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) removeWhere { it.metadata?.isVideoSong == true } else this

        /**
         * Keeps [mediaItemIndex] on the picked song. If the picked song itself is removed,
         * playback starts from the start of the next remaining song.
         */
        internal fun removeWhere(predicate: (MediaItem) -> Boolean): Status {
            val kept = items.filterNot(predicate)
            if (kept.size == items.size) return this
            val pickedRemoved = items.getOrNull(mediaItemIndex)?.let(predicate) == true
            val keptBefore = items.take(mediaItemIndex.coerceAtLeast(0)).count { !predicate(it) }
            return copy(
                items = kept,
                mediaItemIndex = keptBefore.coerceAtMost((kept.size - 1).coerceAtLeast(0)),
                position = if (pickedRemoved) 0L else position,
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
