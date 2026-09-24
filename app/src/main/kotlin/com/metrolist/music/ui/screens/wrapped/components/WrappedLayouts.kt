package com.metrolist.music.ui.screens.wrapped.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.bbhBartle
import kotlinx.coroutines.delay

private val WORD_BOUNDARY = Regex("(?<=-)|\\s+")

/** "3 minutes", with the plural form the language needs. */
@Composable
fun minutesText(playTimeMs: Long?): String {
    val minutes = ((playTimeMs ?: 0L) / 60_000).toInt()
    return pluralStringResource(R.plurals.minute, minutes, minutes)
}

/**
 * The largest size from [maxFontSize] down at which [text] fits [maxLines] lines of [maxWidth]
 * without breaking any word in the middle.
 */
private fun fitFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    maxLines: Int,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
): TextUnit {
    val words = text.split(WORD_BOUNDARY).filter { it.isNotEmpty() }
    var size = maxFontSize.value
    while (size > minFontSize.value) {
        val sized = style.copy(fontSize = size.sp)
        val wordsFit = words.all { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width <= maxWidth }
        if (wordsFit && measurer.measure(text, sized, constraints = Constraints(maxWidth = maxWidth)).lineCount <= maxLines) break
        size -= 2f
    }
    return size.coerceAtLeast(minFontSize.value).sp
}

/** A large page title that shrinks until it fits, never splitting a word, with balanced lines. */
@Composable
fun WrappedTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 44.sp,
    minFontSize: TextUnit = 22.sp,
    maxLines: Int = 3,
) {
    val measurer = rememberTextMeasurer()
    val style =
        LocalTextStyle.current.merge(
            TextStyle(color = Color.White, textAlign = TextAlign.Center, lineHeight = 1.15.em, lineBreak = LineBreak.Heading),
        )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val fontSize =
            remember(text, widthPx, style) {
                fitFontSize(measurer, text, style, widthPx, maxLines, maxFontSize, minFontSize)
            }
        Text(text = text, style = style.copy(fontSize = fontSize), modifier = Modifier.fillMaxWidth())
    }
}

/** Outlined digits in the display font, each line as large as the width allows, all the same size. */
@Composable
fun OutlinedLabel(
    lines: List<String>,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 150.sp,
) {
    val measurer = rememberTextMeasurer()
    val strokeWidth = with(LocalDensity.current) { 1.5.dp.toPx() }
    val style = TextStyle(fontFamily = bbhBartle, color = Color.White, textAlign = TextAlign.Center, drawStyle = Stroke(width = strokeWidth))
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val fontSize =
            remember(lines, widthPx) {
                var size = maxFontSize.value
                while (size > 12f && lines.any { measurer.measure(it, style.copy(fontSize = size.sp), softWrap = false, maxLines = 1).size.width > widthPx }) {
                    size *= 0.94f
                }
                size.sp
            }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = style.copy(fontSize = fontSize, lineHeight = fontSize),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** One row of a top-5 list. */
data class RankedItem(
    val imageUrl: String?,
    val title: String,
    val subtitle: String,
)

@Composable
private fun RankedRow(
    rank: Int,
    item: RankedItem,
    circleImage: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            fontFamily = bbhBartle,
            fontSize = 30.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(if (circleImage) CircleShape else RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotEmpty()) {
                Text(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A title over a ranked list that slides in row by row once the page is shown. */
@Composable
fun WrappedTopListPage(
    title: String,
    items: List<RankedItem>,
    isVisible: Boolean,
    circleImages: Boolean = false,
    background: @Composable () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            visible = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        background()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 200)) + slideInVertically(tween(1000, delayMillis = 200)),
            ) {
                WrappedTitle(text = title, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(28.dp))
            items.forEachIndexed { index, item ->
                AnimatedVisibility(
                    visible = visible,
                    enter =
                        fadeIn(tween(600, delayMillis = 400 + index * 150)) +
                            slideInVertically(tween(600, delayMillis = 400 + index * 150)),
                ) {
                    RankedRow(rank = index + 1, item = item, circleImage = circleImages)
                }
            }
        }
    }
}

/** A small heading, a large picture, a name and a caption: the reveal of a single top item. */
@Composable
fun WrappedTopItemPage(
    heading: String,
    imageUrl: String?,
    name: String,
    subtitle: String?,
    caption: String,
    isVisible: Boolean,
    circleImage: Boolean = false,
    background: @Composable () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) visible = true
    }

    Box(Modifier.fillMaxSize()) {
        background()
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 200)) + slideInVertically(tween(1000, delayMillis = 200)),
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 400)) + slideInVertically(tween(1000, delayMillis = 400)),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(220.dp)
                            .clip(if (circleImage) CircleShape else RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                )
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 600)) + slideInVertically(tween(1000, delayMillis = 600)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineMedium.copy(lineBreak = LineBreak.Heading),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 900)) + slideInVertically(tween(1000, delayMillis = 900)),
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
