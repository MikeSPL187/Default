package com.metrolist.music.ui.utils

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * Like [delay], but also waits for the next frame. Compose stops producing frames while the app is
 * in the background or the screen is off, so polling loops built on this sleep instead of waking the
 * CPU for UI nobody can see.
 */
suspend fun frameAwareDelay(timeMillis: Long) {
    delay(timeMillis)
    withFrameNanos { }
}
