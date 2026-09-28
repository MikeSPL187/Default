/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.metrolist.music.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit animation of the screen a composable sits on, set per navigation destination. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Lets a cover fly from the card that was tapped to the header of the screen it opens: both carry
 * the same [key]. Off screens that are not wired for it, the modifier does nothing.
 */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val transition = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedScope.current
    if (key == null || transition == null || visibility == null) return this
    return with(transition) {
        this@sharedCover.sharedElement(
            sharedContentState = rememberSharedContentState("cover:$key"),
            animatedVisibilityScope = visibility,
        )
    }
}

/** Marks a navigation destination whose covers may fly to or from another screen. */
@Composable
fun AnimatedVisibilityScope.WithSharedCovers(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedScope provides this, content = content)
}
