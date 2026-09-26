package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable

/** An album heard lately track by track and left before its end: where to take it up again. */
@Immutable
data class AlbumProgress(
    val albumId: String,
    val title: String,
    val thumbnailUrl: String?,
    val songCount: Int,
    val trackIndex: Int,
    val lastPlayed: Long,
)
