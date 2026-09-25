package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

/** How much a downloaded song has been listened to, which the offline flow weighs its choice by. */
@Immutable
data class DownloadedSongStats(
    val id: String,
    val playTime: Long,
    val lastPlayed: LocalDateTime?,
)

/** How many of a playlist's songs are on the device, out of all it holds. */
@Immutable
data class PlaylistDownloadCount(
    val playlistId: String,
    val downloaded: Int,
    val total: Int,
)
