package com.metrolist.music.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.NotRecommended
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.notRecommended
import com.metrolist.music.utils.setArtistNotRecommended
import com.metrolist.music.utils.setSongNotRecommended
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun rememberNotRecommended(): State<NotRecommended> {
    val context = LocalContext.current
    val flow = remember(context) { context.dataStore.data.map { it.notRecommended() }.distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initialValue = NotRecommended())
}

internal fun notRecommendedSongMenuItem(
    isHidden: Boolean,
    onClick: () -> Unit,
) = Material3MenuItemData(
    title = { Text(stringResource(if (isHidden) R.string.recommend_again else R.string.dont_recommend_song)) },
    description = {
        Text(stringResource(if (isHidden) R.string.recommend_again_desc else R.string.dont_recommend_song_desc))
    },
    icon = { Icon(painterResource(R.drawable.not_recommended), contentDescription = null) },
    onClick = onClick,
)

internal fun notRecommendedArtistMenuItem(
    isHidden: Boolean,
    onClick: () -> Unit,
) = Material3MenuItemData(
    title = { Text(stringResource(if (isHidden) R.string.recommend_again else R.string.dont_recommend_artist)) },
    description = {
        Text(stringResource(if (isHidden) R.string.recommend_again_desc else R.string.dont_recommend_artist_desc))
    },
    icon = { Icon(painterResource(R.drawable.not_recommended), contentDescription = null) },
    onClick = onClick,
)

/** Toggles a song and drops it from the upcoming recommendations right away. */
suspend fun toggleSongNotRecommended(
    context: Context,
    songId: String,
    hide: Boolean,
    playerConnection: PlayerConnection?,
) {
    context.setSongNotRecommended(songId, hide)
    afterNotRecommendedChanged(context, hide, R.string.not_recommended_song_toast, playerConnection)
}

suspend fun toggleArtistNotRecommended(
    context: Context,
    artistId: String,
    hide: Boolean,
    playerConnection: PlayerConnection?,
) {
    context.setArtistNotRecommended(artistId, hide)
    afterNotRecommendedChanged(context, hide, R.string.not_recommended_artist_toast, playerConnection)
}

private suspend fun afterNotRecommendedChanged(
    context: Context,
    hidden: Boolean,
    toastRes: Int,
    playerConnection: PlayerConnection?,
) {
    if (!hidden) return
    playerConnection?.service?.applyNotRecommended(context.notRecommended())
    Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
}
