package com.metrolist.music.ui.screens.wrapped.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

/**
 * The recap's living background, shared by every page: large soft glows in the theme's colours
 * drifting slowly, [accent] among them, and two gentle waves near the bottom that echo the
 * player's wavy progress bar. Everything moves on whole turns of one cycle, so the loop is seamless.
 */
@Composable
fun WrappedAmbience(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accentGlow by animateColorAsState(accent, tween(900), label = "ambience accent")
    val drift by rememberInfiniteTransition(label = "ambience").animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing)),
        label = "ambience drift",
    )
    val surface = colors.surface
    val secondGlow = colors.tertiaryContainer
    val thirdGlow = colors.secondaryContainer
    val waveColor = colors.primary

    Canvas(modifier.fillMaxSize()) {
        drawRect(surface)
        val w = size.width
        val h = size.height
        glow(accentGlow, 0.55f, Offset(w * (0.25f + 0.12f * sin(drift)), h * (0.12f + 0.05f * cos(2 * drift))), w)
        glow(secondGlow, 0.45f, Offset(w * (0.85f + 0.10f * cos(drift)), h * (0.45f + 0.08f * sin(drift))), w * 0.75f)
        glow(thirdGlow, 0.40f, Offset(w * (0.35f + 0.15f * sin(drift + 2f)), h * (0.95f + 0.04f * cos(2 * drift))), w * 0.9f)

        val amplitude = 8.dp.toPx()
        val wavelength = w / 1.3f
        listOf(Triple(0f, 3, 0.32f), Triple(1.7f, 4, 0.16f)).forEachIndexed { index, (shift, speed, alpha) ->
            val baseline = h * 0.88f + index * 12.dp.toPx()
            val phase = speed * drift + shift
            val path = Path()
            var x = 0f
            path.moveTo(0f, baseline + amplitude * sin(phase))
            while (x < w) {
                x = (x + 6f).coerceAtMost(w)
                path.lineTo(x, baseline + amplitude * sin(x / wavelength * TWO_PI + phase))
            }
            drawPath(path, waveColor.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.glow(
    color: Color,
    alpha: Float,
    center: Offset,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
