/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Enter on a hardware keyboard (a tablet's, a Chromebook's) submits the field, as the keyboard's
 * own search key does; a single-line field otherwise lets it go unanswered.
 */
fun Modifier.submitOnEnter(onSubmit: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) onSubmit()
        true
    }
