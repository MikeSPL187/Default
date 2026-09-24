/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.metrolist.music.ui.screens.wrapped.MessagePair
import com.metrolist.music.ui.screens.wrapped.components.DecorativeCorners
import com.metrolist.music.ui.screens.wrapped.components.WrappedCounterPage
import com.metrolist.music.ui.screens.wrapped.components.highlighted

@Composable
fun WrappedMinutesScreen(
    messagePair: MessagePair?,
    totalMinutes: Long,
    isVisible: Boolean,
) {
    WrappedCounterPage(
        heading = highlighted(messagePair?.tease.orEmpty()),
        count = totalMinutes,
        caption = highlighted(messagePair?.reveal.orEmpty()),
        isVisible = isVisible,
        background = {
            DecorativeCorners(
                isVisible,
                corners = listOf(Alignment.TopStart to 5, Alignment.BottomEnd to 5, Alignment.TopEnd to 3, Alignment.BottomStart to 3),
                spread = 150,
            )
        },
    )
}
