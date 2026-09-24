/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import com.metrolist.music.db.entities.AlbumPlayStats
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.SongWithStats

data class WrappedState(
    val totalMinutes: Long = 0,
    /** Days the period has lasted so far, to judge [totalMinutes] per day. */
    val elapsedDays: Long = 1,
    /** Lines of the large period label, see [bigLabel]. */
    val bigLabel: List<String> = emptyList(),
    val topSongs: List<SongWithStats> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val topAlbums: List<AlbumPlayStats> = emptyList(),
    val uniqueSongCount: Int = 0,
    val uniqueArtistCount: Int = 0,
    val totalAlbums: Int = 0,
    val isDataReady: Boolean = false,
    val trackMap: Map<WrappedScreenType, String?> = emptyMap(),
    val playlistCreationState: PlaylistCreationState = PlaylistCreationState.Idle
)
