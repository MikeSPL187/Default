/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.metrolist.music.video.VideoPlayback
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The player's music video, kept by the activity instead of by the player's layout. Turning the
 * screen rebuilds the activity and swaps the player between its upright and sideways layouts; the
 * picture player and full screen outlive both, so the video neither starts over nor closes.
 */
@HiltViewModel
class PlayerVideoViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var playback by mutableStateOf<VideoPlayback?>(null)
        private set

    var fullscreen by mutableStateOf(false)

    private var audio: Player? = null
    private var users = 0
    private var release: Job? = null

    /** The picture player for the song in [player], made on first use. Each call is matched by [letGo]. */
    fun use(player: Player): VideoPlayback {
        users++
        release?.cancel()
        release = null
        playback?.takeIf { audio === player }?.let { return it }
        drop()
        audio = player
        return VideoPlayback(context, player).also { playback = it }
    }

    /** Lets go of the picture player; it lingers a moment in case its layout is only being rebuilt. */
    fun letGo() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        release?.cancel()
        release =
            viewModelScope.launch {
                delay(RELEASE_GRACE_MS)
                drop()
            }
    }

    private fun drop() {
        playback?.release()
        playback = null
        audio = null
        fullscreen = false
    }

    override fun onCleared() = drop()

    private companion object {
        const val RELEASE_GRACE_MS = 2_000L
    }
}
