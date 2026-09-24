/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun AnimatedDecorativeElement(modifier: Modifier = Modifier, isVisible: Boolean) {
    val rotation = remember { Animatable(0f) }
    val shapeType = remember { Random.nextInt(3) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(Random.nextLong(500))
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(Random.nextInt(1000, 3000)),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Canvas(modifier.graphicsLayer { rotationZ = rotation.value }) {
        val strokeWidth = 2.dp.toPx()
        when (shapeType) {
            0 -> drawArc(color, 0f, 90f, false, style = Stroke(strokeWidth))
            1 -> drawCircle(color, style = Stroke(strokeWidth))
            2 -> drawRect(color, style = Stroke(strokeWidth))
        }
    }
}

/**
 * A few spinning outlines gathered in the given corners, each corner with its count. Placed once,
 * so they hold still while the page recomposes, for example during a counting animation.
 */
@Composable
fun DecorativeCorners(
    isVisible: Boolean,
    corners: List<Pair<Alignment, Int>> = listOf(Alignment.TopStart to 3, Alignment.BottomEnd to 4),
    spread: Int = 120,
) {
    val placements =
        remember {
            corners.map { (alignment, count) ->
                alignment to List(count) { Triple(Random.nextInt(0, spread), Random.nextInt(0, spread), Random.nextInt(20, 90)) }
            }
        }
    Box(Modifier.fillMaxSize()) {
        placements.forEach { (alignment, shapes) ->
            Box(Modifier.align(alignment)) {
                shapes.forEach { (x, y, size) ->
                    val padding =
                        when (alignment) {
                            Alignment.TopStart -> PaddingValues(start = x.dp, top = y.dp)
                            Alignment.TopEnd -> PaddingValues(end = x.dp, top = y.dp)
                            Alignment.BottomStart -> PaddingValues(start = x.dp, bottom = y.dp)
                            else -> PaddingValues(end = x.dp, bottom = y.dp)
                        }
                    AnimatedDecorativeElement(Modifier.padding(padding).size(size.dp), isVisible)
                }
            }
        }
    }
}
