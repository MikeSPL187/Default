package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable

/** An album's listening in a period, built from the played songs so albums never opened still count. */
@Immutable
data class AlbumPlayStats(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val timeListened: Long,
)
