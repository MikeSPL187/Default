package com.metrolist.music.ui.screens.wrapped.pages

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.ui.screens.wrapped.LocalWrappedManager
import com.metrolist.music.ui.screens.wrapped.PlaylistCreationState
import com.metrolist.music.ui.screens.wrapped.components.WrappedHeading
import com.metrolist.music.ui.screens.wrapped.components.isDarkSurface
import com.metrolist.music.ui.screens.wrapped.playlistName
import com.metrolist.music.ui.screens.wrapped.renderWrappedCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PlaylistPage() {
    val manager = LocalWrappedManager.current
    val state by manager.state.collectAsStateWithLifecycle()
    val playlistCreationState = state.playlistCreationState

    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val playlistName = remember(manager) { manager.period.playlistName(context, manager.now) }
    // A light tone of the theme colour reads well on the cover's dark background in either theme.
    val coverAccent = (if (isDarkSurface()) colors.primary else colors.inversePrimary).toArgb()
    val cover by produceState<Bitmap?>(initialValue = null, state.bigLabel, coverAccent) {
        value = withContext(Dispatchers.Default) { renderWrappedCover(context, state.bigLabel, coverAccent) }
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        startAnimation = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 200),
        label = "playlist page alpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WrappedHeading(stringResource(R.string.wrapped_playlist_ready))
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(256.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceContainerHighest),
            ) {
                cover?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.album_cover_desc),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = remember(playlistName) { playlistName.withoutOrphan() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = {
                    val image = cover
                    if (playlistCreationState == PlaylistCreationState.Idle && image != null) {
                        manager.createPlaylist(image, playlistName)
                    }
                },
                enabled = cover != null,
                modifier = Modifier.height(50.dp)
            ) {
                when (playlistCreationState) {
                    is PlaylistCreationState.Idle -> Text(
                        text = stringResource(R.string.wrapped_create_playlist),
                        style = MaterialTheme.typography.titleMedium
                    )
                    is PlaylistCreationState.Creating -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                    is PlaylistCreationState.Success -> Text(
                        text = stringResource(R.string.wrapped_playlist_saved),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/** Keeps a short last word such as "г." on the line before it. */
private fun String.withoutOrphan(): String {
    val lastSpace = lastIndexOf(' ')
    return if (lastSpace > 0 && length - lastSpace <= 4) replaceRange(lastSpace, lastSpace + 1, "\u00A0") else this
}
