package com.metrolist.music.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ui.component.PlayPauseIcon
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.video.VideoPlayback
import kotlinx.coroutines.delay

/**
 * "Song | Video": whether the player shows the cover or the music video. Offered only for songs
 * that have a video.
 */
@Composable
fun SongVideoSwitch(
    video: Boolean,
    onChange: (Boolean) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier =
            modifier
                .background(contentColor.copy(alpha = 0.12f), CircleShape)
                .padding(3.dp)
                .selectableGroup(),
    ) {
        listOf(false to R.string.video_switch_song, true to R.string.video_switch_video).forEach { (isVideo, label) ->
            val selected = video == isVideo
            val background by animateColorAsState(if (selected) contentColor else Color.Transparent, label = "switch background")
            val text by animateColorAsState(
                if (!selected) contentColor else if (contentColor.isLight()) Color.Black else Color.White,
                label = "switch text",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .background(background, CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onChange(isVideo)
                                }
                            },
                        ).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = text)
            }
        }
    }
}

private fun Color.isLight() = red * 0.299f + green * 0.587f + blue * 0.114f > 0.5f

/** The picture of [playback], kept on screen while it plays. */
@Composable
fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
    val playing by (playerConnection?.isEffectivelyPlaying ?: return).collectAsStateWithLifecycle()
    AndroidView(
        factory = { context -> TextureView(context).also(playback::attach) },
        update = { view -> view.keepScreenOn = playing },
        onRelease = playback::detach,
        modifier = modifier,
    )
}

/**
 * The music video over the cover: it fades in once its first picture is ready, with a quiet
 * spinner while it loads and a button to watch it on the whole screen.
 */
@Composable
fun VideoInCover(
    video: VideoPlayback,
    showing: Boolean,
    onFullscreen: () -> Unit,
) {
    val status by video.status.collectAsStateWithLifecycle()
    val alpha by animateFloatAsState(if (showing) 1f else 0f, label = "video fade")
    Box(Modifier.fillMaxSize()) {
        VideoSurface(video, Modifier.fillMaxSize().alpha(alpha))
        when {
            status == VideoPlayback.Status.FAILED ->
                Text(
                    stringResource(R.string.video_unavailable),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            !showing ->
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(6.dp)
                            .size(18.dp),
                )
            else ->
                IconButton(
                    onClick = onFullscreen,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            .size(40.dp),
                ) {
                    Icon(painterResource(R.drawable.fullscreen), contentDescription = stringResource(R.string.video_fullscreen), tint = Color.White)
                }
        }
    }
}

/**
 * The video on the whole screen, turned sideways for a wide picture, with the song's controls on
 * a tap. The song keeps playing as before; closing this returns to the player.
 */
@Composable
fun VideoFullscreen(
    playback: VideoPlayback,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val metadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playing by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val aspectRatio by playback.aspectRatio.collectAsStateWithLifecycle()
    val showing by playback.showing.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var interactedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // A wide picture turns the screen sideways; the orientation it had comes back on closing.
    DisposableEffect(aspectRatio > 1f) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        if (aspectRatio > 1f) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (previous != null) activity?.requestedOrientation = previous }
    }
    // The controls step aside after a few seconds of watching.
    LaunchedEffect(controls, interactedAt, playing) {
        if (controls && playing) {
            delay(CONTROLS_TIMEOUT_MS)
            controls = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        controls = !controls
                        interactedAt = System.currentTimeMillis()
                    },
        ) {
            VideoSurface(playback, Modifier.aspectRatio(aspectRatio))
            // Between songs the next picture is on its way.
            if (!showing) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .safeDrawingPadding(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp)) {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.fullscreen_exit), contentDescription = stringResource(R.string.video_exit_fullscreen), tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(metadata?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                metadata?.artists?.joinToString { it.name }.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                interactedAt = System.currentTimeMillis()
                                playerConnection.seekToPrevious()
                            },
                            enabled = canSkipPrevious,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(painterResource(R.drawable.skip_previous), contentDescription = stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        FilledIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                interactedAt = System.currentTimeMillis()
                                playerConnection.togglePlayPause()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.size(72.dp),
                        ) {
                            PlayPauseIcon(playing = playing, size = 36.dp)
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                interactedAt = System.currentTimeMillis()
                                playerConnection.seekToNext()
                            },
                            enabled = canSkipNext,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(painterResource(R.drawable.skip_next), contentDescription = stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                    FullscreenSeekBar(
                        onInteract = { interactedAt = System.currentTimeMillis() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenSeekBar(
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            delay(POSITION_INTERVAL_MS)
        }
    }
    val known = duration != C.TIME_UNSET && duration > 0
    val shown = dragging ?: if (known) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = {
                dragging = it
                onInteract()
            },
            onValueChangeFinished = {
                dragging?.let { if (known) player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = known,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(makeTimeString(if (known) (shown * duration).toLong() else position), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Color.White)
            Spacer(Modifier.weight(1f))
            if (known) Text(makeTimeString(duration), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Color.White)
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val POSITION_INTERVAL_MS = 250L
