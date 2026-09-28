/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * One line that shrinks just enough to stay whole instead of being cut to "All ti…": for short
 * labels in tight places, where a longer translation or a larger system font would not fit.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = 9.sp,
) {
    val size = style.fontSize.takeIf { it.isSpecified } ?: 14.sp
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = if (color != Color.Unspecified) color else LocalContentColor.current, textAlign = textAlign),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = size, stepSize = 0.5.sp),
    )
}

/** The label of a segmented button: a segment is a third or a quarter of the row. */
@Composable
fun SegmentLabel(text: String) = FitText(text, textAlign = TextAlign.Center)
