package com.metrolist.music.offline

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the app is working from what is on the device: there is no connection, or the user
 * chose to play downloads only. Screens use it to show what can play and to dim what cannot.
 */
data class OfflineMode(
    val noNetwork: Boolean = false,
    val downloadedOnly: Boolean = false,
) {
    val active: Boolean get() = noNetwork || downloadedOnly
}

val LocalOfflineMode = compositionLocalOf { OfflineMode() }
