package com.metrolist.music.ui.screens.wrapped.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.ui.theme.bbhBartle
import com.metrolist.music.ui.theme.extractThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// The recap takes its colours from the app theme (Material You, light or dark, pure black) so it
// feels like part of the app; artwork pages tint themselves like the player's gradient background.

/** Joins the parts of a hyphenated word so a line never breaks inside it ("топ-исполнители"). */
private fun String.keepHyphenatedWords(): String = replace("-", "-⁠")

/** "3 minutes", with the plural form the language needs. */
@Composable
fun minutesText(playTimeMs: Long?): String {
    val minutes = ((playTimeMs ?: 0L) / 60_000).toInt()
    return pluralStringResource(R.plurals.minute, minutes, minutes)
}

/** The main colour of an artwork, as the player picks it for its gradient; null until known. */
@Composable
fun rememberArtworkAccent(url: String?): Color? {
    val context = LocalContext.current
    val accent by produceState<Color?>(initialValue = null, url) {
        if (url == null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context).data(url).size(128, 128).allowHardware(false).build()
                    context.imageLoader.execute(request).image?.toBitmap()?.extractThemeColor()
                }.getOrNull()
            }
    }
    return accent
}

/** Text with `**bold**` parts, which are also drawn in the accent colour. */
@Composable
fun highlighted(text: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return remember(text, accent) {
        buildAnnotatedString {
            var bold = false
            text.split("**").forEach { part ->
                val whole = part.keepHyphenatedWords()
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent)) { append(whole) } else append(whole)
                bold = !bold
            }
        }
    }
}

/**
 * The largest size from [maxFontSize] down at which [text] fits [maxLines] lines of [maxWidth]
 * with every word whole.
 */
private fun fitFontSize(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    maxWidth: Int,
    maxLines: Int,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
): TextUnit {
    val words = text.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    var size = maxFontSize.value
    while (size > minFontSize.value) {
        val sized = style.copy(fontSize = size.sp)
        val wordsFit = words.all { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width <= maxWidth }
        if (wordsFit && measurer.measure(text, sized, constraints = Constraints(maxWidth = maxWidth)).lineCount <= maxLines) break
        size -= 2f
    }
    return size.coerceAtLeast(minFontSize.value).sp
}

/**
 * The one heading style of every recap page: the same size and weight everywhere, shrinking only
 * when a long heading would not fit, and never splitting a word.
 */
@Composable
fun WrappedHeading(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    maxLines: Int = 3,
) {
    val measurer = rememberTextMeasurer()
    val headingStyle =
        style.merge(
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val fontSize =
            remember(text, widthPx, headingStyle) {
                fitFontSize(measurer, text, headingStyle, widthPx, maxLines, headingStyle.fontSize, 16.sp)
            }
        Text(
            text = text,
            style = headingStyle.copy(fontSize = fontSize, lineHeight = headingStyle.lineHeight * (fontSize.value / headingStyle.fontSize.value)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun WrappedHeading(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    maxLines: Int = 3,
) {
    WrappedHeading(remember(text) { AnnotatedString(text.keepHyphenatedWords()) }, modifier, style, maxLines)
}

/** Heavy digits in the display font, each line as large as the width allows, all the same size. */
@Composable
fun DisplayDigits(
    lines: List<String>,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 140.sp,
    outlined: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val measurer = rememberTextMeasurer()
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    val style =
        TextStyle(
            fontFamily = bbhBartle,
            color = color,
            textAlign = TextAlign.Center,
            drawStyle = if (outlined) Stroke(width = strokeWidth) else null,
        )
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

/** A heading, a number counting up from zero in heavy digits, and a line under it. */
@Composable
fun WrappedCounterPage(
    heading: AnnotatedString,
    count: Long,
    caption: AnnotatedString,
    isVisible: Boolean,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(isVisible, count) {
        if (isVisible && count > 0) animated.animateTo(count.toFloat(), tween(1500, easing = FastOutSlowInEasing))
    }
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            WrappedHeading(heading)
            Spacer(Modifier.height(24.dp))
            // Sized for the final number so the layout holds still while it counts up.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val base = TextStyle(fontFamily = bbhBartle, color = colors.primary, textAlign = TextAlign.Center)
                val widthPx = constraints.maxWidth
                val fontSize =
                    remember(count, widthPx) {
                        var size = 120f
                        while (size > 24f && measurer.measure(count.toString(), base.copy(fontSize = size.sp)).size.width > widthPx) size *= 0.95f
                        size.sp
                    }
                Text(
                    text = animated.value.toLong().toString(),
                    style = base.copy(fontSize = fontSize, lineHeight = fontSize * 1.08f),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One row of a top-5 list. */
data class RankedItem(
    val imageUrl: String?,
    val title: String,
    val subtitle: String,
)

/** Corner shapes of a card in a joined group, as the app's menus draw them. */
private fun groupShape(index: Int, count: Int) =
    when {
        count == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }

@Composable
private fun RankedRow(
    rank: Int,
    item: RankedItem,
    circleImage: Boolean,
    shape: RoundedCornerShape,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                fontFamily = bbhBartle,
                fontSize = 26.sp,
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(if (circleImage) CircleShape else RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerHighest),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A title over a ranked list whose cards slide in one by one once the page is shown. */
@Composable
fun WrappedTopListPage(
    title: String,
    items: List<RankedItem>,
    isVisible: Boolean,
    circleImages: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            visible = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 200)) + slideInVertically(tween(1000, delayMillis = 200)),
            ) {
                WrappedHeading(title, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { index, item ->
                    AnimatedVisibility(
                        visible = visible,
                        enter =
                            fadeIn(tween(600, delayMillis = 400 + index * 150)) +
                                slideInVertically(tween(600, delayMillis = 400 + index * 150)),
                    ) {
                        RankedRow(rank = index + 1, item = item, circleImage = circleImages, shape = groupShape(index, items.size))
                    }
                }
            }
        }
    }
}

/** The reveal of a single top item: a heading, the artwork, the name and a pill with the listening time. */
@Composable
fun WrappedTopItemPage(
    heading: String,
    imageUrl: String?,
    name: String,
    subtitle: String?,
    caption: String,
    isVisible: Boolean,
    circleImage: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) visible = true
    }
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 200)) + slideInVertically(tween(1000, delayMillis = 200)),
            ) {
                WrappedHeading(heading)
            }
            Spacer(Modifier.height(28.dp))
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
                            .size(240.dp)
                            .clip(if (circleImage) CircleShape else RoundedCornerShape(28.dp))
                            .background(colors.surfaceContainerHighest),
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
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, delayMillis = 900)) + slideInVertically(tween(1000, delayMillis = 900)),
            ) {
                Surface(shape = CircleShape, color = colors.secondaryContainer) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Whether the app is showing its dark theme, judged from the surface colour. */
@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
