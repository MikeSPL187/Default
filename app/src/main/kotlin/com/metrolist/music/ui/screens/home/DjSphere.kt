package com.metrolist.music.ui.screens.home

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.R
import com.metrolist.music.dj.DjMode
import com.metrolist.music.dj.DjMood
import com.metrolist.music.ui.component.PlayPauseIcon
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The sphere's colours: four that flow inside it and the dark it sits on. */
@Immutable
data class SpherePalette(
    val colors: List<Color>,
    val base: Color,
)

/** A palette around [accent]: warmer and cooler neighbours of it, a touch of [partner] and a light tint. */
fun spherePalette(
    accent: Color,
    partner: Color,
): SpherePalette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)

    fun tone(
        hueShift: Float,
        saturation: Float,
        value: Float,
    ) = Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf((hsv[0] + hueShift + 360f) % 360f, (hsv[1] * saturation).coerceIn(0.3f, 1f), (hsv[2] * value).coerceIn(0.5f, 1f)),
        ),
    )
    return SpherePalette(
        colors = listOf(tone(0f, 1f, 1.1f), tone(-28f, 1.15f, 1f), lerp(partner, tone(-110f, 1f, 1f), 0.5f), tone(12f, 0.55f, 1.25f)),
        base = lerp(tone(-20f, 1f, 0.6f), Color.Black, 0.55f),
    )
}

/**
 * Seconds for the sphere, running faster while music plays. It ticks every frame only while
 * playing; at rest a few times a second is smooth enough and spares the battery. With system
 * animations turned off it stands still.
 */
@Composable
private fun rememberSphereClock(energy: State<Float>): State<Float> {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    return produceState(0f, still) {
        if (still) return@produceState
        var last = withFrameNanos { it }
        while (true) {
            if (energy.value < 0.01f) delay(IDLE_FRAME_MS)
            withFrameNanos { now ->
                value += (now - last) / 1_000_000_000f * (IDLE_SPEED + (PLAY_SPEED - IDLE_SPEED) * energy.value)
                last = now
            }
        }
    }
}

/** A soft glowing drop that breathes; while music plays it moves faster and sends out rings. */
@Composable
private fun Sphere(
    palette: SpherePalette,
    energy: State<Float>,
    clock: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = palette.colors.map { animateColorAsState(it, tween(COLOR_MS), label = "sphere colour").value }
    val base by animateColorAsState(palette.base, tween(COLOR_MS), label = "sphere base")
    val shape = remember { Path() }
    val ring = remember { Path() }
    Canvas(modifier) {
        val t = clock.value
        val e = energy.value
        val c = center
        val r = size.minDimension * SPHERE_RADIUS
        val amp = 0.028f + 0.045f * e

        val glow = r * (1.45f + 0.1f * e)
        drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha = 0.42f + 0.25f * e), Color.Transparent), c, glow), glow, c)
        if (e > 0.01f) {
            repeat(RINGS) { i ->
                val k = (t * RING_RATE + i.toFloat() / RINGS) % 1f
                ring.blob(c, r * (1.04f + 0.32f * k), amp * 0.8f, t + i * 0.9f)
                drawPath(ring, colors[i].copy(alpha = (1f - k) * 0.55f * e), style = Stroke(1.6.dp.toPx()))
            }
        }

        shape.blob(c, r, amp, t)
        clipPath(shape) {
            drawRect(base)
            colors.forEachIndexed { i, color ->
                val a = t * 0.55f + i * (PI / 2).toFloat()
                val o = Offset(c.x + cos(a) * r * 0.42f, c.y + sin(a * 1.3f + i) * r * 0.42f)
                drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), o, r), r, o)
            }
            drawRect(Brush.radialGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f), center = Offset(c.x + r * 0.25f, c.y + r * 0.45f), radius = r * 1.5f))
            drawRect(Brush.radialGradient(0f to Color.White.copy(alpha = 0.5f), 0.5f to Color.Transparent, center = Offset(c.x - r * 0.38f, c.y - r * 0.48f), radius = r * 1.2f))
        }
        drawPath(shape, Color.White.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
    }
}

/** A closed smooth outline around [c] whose radius waves by [amp] as [t] runs. */
private fun Path.blob(
    c: Offset,
    r: Float,
    amp: Float,
    t: Float,
) {
    rewind()
    val xs = FloatArray(BLOB_POINTS)
    val ys = FloatArray(BLOB_POINTS)
    for (i in 0 until BLOB_POINTS) {
        val a = i * 2f * PI.toFloat() / BLOB_POINTS
        val k = 1f + amp * (sin(3 * a + t) * 0.6f + sin(5 * a - t * 1.7f) * 0.4f)
        xs[i] = c.x + r * k * cos(a)
        ys[i] = c.y + r * k * sin(a)
    }
    moveTo(xs[0], ys[0])
    // Catmull-Rom through the points, as cubic Béziers.
    for (i in 0 until BLOB_POINTS) {
        val p0 = (i - 1 + BLOB_POINTS) % BLOB_POINTS
        val p2 = (i + 1) % BLOB_POINTS
        val p3 = (i + 2) % BLOB_POINTS
        cubicTo(
            xs[i] + (xs[p2] - xs[p0]) / 6,
            ys[i] + (ys[p2] - ys[p0]) / 6,
            xs[p2] - (xs[p3] - xs[i]) / 6,
            ys[p2] - (ys[p3] - ys[i]) / 6,
            xs[p2],
            ys[p2],
        )
    }
    close()
}

/**
 * The DJ at the top of home: the sphere with its button, what it plays, and at rest the covers of
 * songs it may bring. Playing, three reactions teach it on the spot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DjHero(
    active: Boolean,
    isPlaying: Boolean,
    starting: Boolean,
    title: String,
    subtitle: String,
    palette: SpherePalette,
    previews: List<String>,
    liked: Boolean,
    onPlay: () -> Unit,
    onTune: () -> Unit,
    onDislike: () -> Unit,
    onFavour: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    liveLabel: String? = null,
    showTune: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val playing = active && isPlaying
    val energy = animateFloatAsState(if (playing) 1f else 0f, tween(ENERGY_MS), label = "sphere energy")
    val clock = rememberSphereClock(energy)
    val press = remember { MutableInteractionSource() }
    val play = {
        haptic.performHapticFeedback(if (active) HapticFeedbackType.ContextClick else HapticFeedbackType.Confirm)
        onPlay()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SPHERE_BOX),
        ) {
            val spread = min(maxWidth / 360.dp, 1f)
            PREVIEW_SPOTS.zip(previews).forEachIndexed { i, (spot, url) ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .offset(spot.x * spread, spot.y)
                            .graphicsLayer {
                                translationY = sin(clock.value * 0.8f + i * 1.7f) * 4.dp.toPx()
                                rotationZ = spot.rotation
                                alpha = 1f - energy.value
                            }.size(spot.size)
                            .shadow(8.dp, RoundedCornerShape(spot.size * 0.28f))
                            .clip(RoundedCornerShape(spot.size * 0.28f)),
                )
            }
            Sphere(
                palette = palette,
                energy = energy,
                clock = clock,
                modifier =
                    Modifier
                        .size(SPHERE_BOX)
                        .combinedClickable(
                            interactionSource = press,
                            indication = null,
                            onClick = play,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTune()
                            },
                        ),
            )
            val corner by animateDpAsState(if (playing) 23.dp else 34.dp, tween(300), label = "button corner")
            Surface(
                onClick = play,
                shape = RoundedCornerShape(corner),
                color = Color.White,
                contentColor = palette.base,
                shadowElevation = 8.dp,
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (starting && !active) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp, color = palette.base, modifier = Modifier.size(26.dp))
                    } else {
                        PlayPauseIcon(playing = playing, size = 34.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(LiveRed),
                )
            }
            Text(
                (if (active) liveLabel ?: stringResource(R.string.dj_live) else label ?: stringResource(R.string.dj_title)).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedContent(
            targetState = title to subtitle,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
            label = "dj text",
        ) { (heading, line) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    heading,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(targetState = active, label = "dj actions") { on ->
            if (on) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DjAction(R.drawable.thumb_down, R.string.dj_dislike) {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        onDislike()
                    }
                    if (showTune) {
                        DjAction(R.drawable.tune, R.string.dj_tune) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onTune()
                        }
                    }
                    DjAction(if (liked) R.drawable.favorite else R.drawable.favorite_border, R.string.dj_favour, highlighted = liked) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onFavour()
                    }
                }
            } else if (showTune) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onTune()
                    },
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(painterResource(R.drawable.tune), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dj_tune))
                }
            }
        }
    }
}

@Composable
private fun DjAction(
    icon: Int,
    label: Int,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            colors =
                if (highlighted) {
                    IconButtonDefaults.filledTonalIconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(painterResource(icon), contentDescription = stringResource(label))
        }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** What the DJ plays and from which mood; every change applies to the songs still to come. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DjTuneSheet(
    mode: DjMode,
    moods: List<HomePage.Chip>,
    mood: DjMood?,
    onMode: (DjMode) -> Unit,
    onMood: (HomePage.Chip?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.dj_tune_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.dj_tune_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            SheetLabel(R.string.dj_tune_what)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DjMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == mode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onMode(option)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = DjMode.entries.size),
                        label = { Text(stringResource(option.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            if (moods.isNotEmpty()) {
                SheetLabel(R.string.dj_tune_mood)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    FilterChip(
                        selected = mood == null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onMood(null)
                        },
                        label = { Text(stringResource(R.string.dj_mood_any)) },
                        leadingIcon = { Icon(painterResource(R.drawable.all_inclusive), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    moods.forEach { chip ->
                        FilterChip(
                            selected = mood?.title == chip.title,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onMood(chip)
                            },
                            label = { Text(chip.title) },
                            leadingIcon = { Icon(painterResource(moodIcon(chip.title)), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reset)) }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.home_done)) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

val DjMode.label
    get() =
        when (this) {
            DjMode.FAVOURITES -> R.string.dj_mode_favourites
            DjMode.MIXED -> R.string.dj_mode_mixed
            DjMode.DISCOVER -> R.string.dj_mode_discover
        }

val DjMode.description
    get() =
        when (this) {
            DjMode.FAVOURITES -> R.string.dj_mode_favourites_desc
            DjMode.MIXED -> R.string.dj_mode_mixed_desc
            DjMode.DISCOVER -> R.string.dj_mode_discover_desc
        }

/** Where a preview cover floats around the sphere, from its centre. */
private data class Spot(
    val x: Dp,
    val y: Dp,
    val size: Dp,
    val rotation: Float,
)

private val PREVIEW_SPOTS =
    listOf(
        Spot((-126).dp, (-58).dp, 40.dp, -10f),
        Spot(124.dp, (-70).dp, 34.dp, 12f),
        Spot((-134).dp, 56.dp, 30.dp, 8f),
        Spot(128.dp, 44.dp, 42.dp, -8f),
    )

private val LiveRed = Color(0xFFFF6B5B)
private val SPHERE_BOX = 232.dp
private const val SPHERE_RADIUS = 0.38f
private const val BLOB_POINTS = 12
private const val RINGS = 3
private const val RING_RATE = 0.35f
private const val IDLE_SPEED = 0.35f
private const val PLAY_SPEED = 1.25f
private const val IDLE_FRAME_MS = 45L
private const val COLOR_MS = 900
private const val ENERGY_MS = 700
