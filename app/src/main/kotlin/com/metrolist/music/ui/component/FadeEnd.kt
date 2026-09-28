/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fades the trailing edge out, so a line too long for its place (a scrolling title that has
 * stopped) ends softly instead of in the middle of a letter. Put it on a box as wide as the
 * space: a short line never reaches the edge and stays untouched.
 */
fun Modifier.fadeEnd(width: Dp = 20.dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fade = width.toPx().coerceAtMost(size.width)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val start = if (rtl) fade else size.width - fade
            val end = if (rtl) 0f else size.width
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = end),
                blendMode = BlendMode.DstIn,
            )
        }
